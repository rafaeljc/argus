# ALB `preserve` for client IP resolution behind CloudFront

- Status: accepted
- Date: 2026-10-01
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: `#108` moved client-IP resolution from the spoofable
leftmost `X-Forwarded-For` entry to Tomcat's `RemoteIpValve`
(`server.forward-headers-strategy: native`), trusting it to walk the header
right-to-left and stop at the first entry outside its trusted-proxy range. In
production it still resolves the wrong address — CloudWatch logged an
`ip_address` that was not a viewer, but an AWS CloudFront origin-facing address.

## Context and Problem Statement

Two hops both write `X-Forwarded-For` between a viewer and Tomcat:

1. CloudFront **appends** the viewer IP to whatever the client sent.
2. The ALB then **appends its own TCP peer** — the CloudFront edge that
   dialed it — because `routing.http.xff_header_processing.mode` defaults to
   `append` and nothing in `infra/argus/constructs/backend_service.py`
   overrode it.

Tomcat receives `…, <viewerIP>, <cloudFrontEdgeIP>`. Its TCP peer is the ALB
(`10.0.0.0/16`, inside `RemoteIpValve`'s default `internalProxies`), so the
valve pops one entry off the right and stops at the CloudFront edge address —
public, so not a trusted proxy. The valve behaves exactly as designed; it is
handed one hop too many.

**Impact is not cosmetic.** `BucketResolver` keys `auth.signup`, `auth.login`,
`auth.reset`, and `unauth.global` on this value, and buckets are shared in
Redis (ADR 0006). Every viewer behind one CloudFront POP shares a single
bucket, so NFR-Sec7's "5 signups / hour / IP" is effectively 5 per hour for a
whole region.

Can client IP resolution be corrected for the actual chain in front of this
backend — CloudFront in front of an internal ALB — without changing the
backend at all?

## Decision Drivers

- **DD-1 — No backend change.** `RemoteIpValve` and `BucketResolver` already
  implement the intended trust model correctly; the defect is in the request
  chain the backend receives, not in how it reads that chain.
- **DD-2 — No new spoofing surface.** Whatever closes this gap must not let a
  client control the value the backend ultimately trusts.
- **DD-3 — Minimal operational surface.** Prefer a single existing knob over
  adding a new header, a new backend resolver, or a new origin policy.
- **DD-4 — The assumption must be guarded.** The backend cannot observe this
  attribute, so nothing in its test suite can catch a regression; the guard
  has to live wherever the fix lives.

## Considered Options

- **Option 1 — Keep `append`, widen Tomcat's `trustedProxies`** to also cover
  CloudFront's published ranges, so the valve skips the CloudFront-edge entry
  the ALB appends and lands on the viewer behind it.
- **Option 2 — ALB `routing.http.xff_header_processing.mode: preserve`.**
  Stop the ALB from appending its peer; CloudFront becomes the sole writer.
- **Option 3 — `CloudFront-Viewer-Address`.** AWS's own
  [documented recommendation](https://repost.aws/articles/AR-VvhzdKVRgOIYl4SJMTcHg/how-to-identify-the-real-client-ip-on-alb-when-using-cloudfront-as-a-front-end):
  read a dedicated CloudFront-origin header instead of `X-Forwarded-For`.

## Decision Outcome

Chosen option: **Option 2 — ALB `preserve`.**

It satisfies DD-1 outright: nothing under `backend/` changes.
`RemoteIpValve`'s right-to-left walk and `BucketResolver`'s
`request.getRemoteAddr()` calls become correct as soon as the header shape is
right, because CloudFront always appends the viewer IP **last** — the
rightmost entry is the viewer by construction, and anything a client forges
is pushed left of it, satisfying DD-2 the same way the existing valve logic
already does. It satisfies DD-3: one CDK property on a resource already
being created, no new header, no new backend resolver, no origin-policy
change. DD-4 is met by the CDK template assertion added alongside this
change (`test_the_load_balancer_does_not_rewrite_the_forwarded_for_header` in
`infra/tests/test_compute.py`) — the only place a regression could be caught,
since the backend has no way to observe this ALB attribute.

Option 1 was rejected even though it also satisfies DD-1: it requires
enumerating and maintaining AWS's published CloudFront IP ranges inside
`application.yaml` (or a profile-specific override), a list AWS rotates
without notice, and the project has no mechanism to refresh it. It fails
DD-3 for an ongoing maintenance cost Option 2 does not carry.

Option 3 was rejected on inspection despite being AWS's documented
recommendation: it requires more change than Option 2 for the same problem.
Adopting it means a new origin request policy (the managed
`AllViewerExceptHostHeader` policy was checked directly —
`aws cloudfront get-origin-request-policy` returns
`{"HeaderBehavior": "allExcept", "Headers": ["host"]}`, which forwards no
CloudFront-origin headers at all, contrary to its own documentation's prose)
plus a new backend resolver parsing `IP:port` / `[IPv6]:port`. It fails DD-1
and DD-3 relative to Option 2, which is a single property on a resource
already being created — recorded as the standing alternative below.

### Consequences

- Good, because `request.getRemoteAddr()` resolves to the real viewer IP in
  production with zero lines of backend code touched (DD-1).
- Good, because `BucketResolver`'s IP-keyed buckets (`auth.signup`,
  `auth.login`, `auth.reset`, `unauth.global`) return to being per-viewer
  instead of per-CloudFront-POP, restoring NFR-Sec7's intended bound.
- Good, because the fix is one CDK property on a resource the project
  already owns and deploys — no new AWS resource, no new backend dependency.
- Bad, because correctness now depends on an ALB attribute that is entirely
  invisible from the backend's own code and tests. `BucketResolver.java` and
  `TrustedProxyClientIpIT` still describe the trust model in terms that
  predate this attribute and do not mention it — that coupling is recorded
  here and next to the CDK property itself, deliberately, to keep this
  change infra-only rather than editing those files to cross-reference an
  infra concern.
- Bad, because `preserve` and `routing.http.xff_client_port.enabled` are
  mutually exclusive — `preserve` means the ALB writes nothing into the
  header at all, so client-port preservation cannot be layered on top later
  without revisiting this decision.
- Neutral, because `preserve`'s safety is conditional on a trust boundary
  this ADR does not create: the ALB is `internal`, sits in
  `PRIVATE_WITH_EGRESS` subnets, and its security group admits only the
  `com.amazonaws.global.cloudfront.origin-facing` prefix list
  (`infra/argus/stacks/compute.py`). `preserve` is correct only because
  nothing but CloudFront can ever open a connection to this ALB.

### Confirmation

- `cd infra && pytest` is green, including
  `test_the_load_balancer_does_not_rewrite_the_forwarded_for_header`, which
  asserts `LoadBalancerAttributes` contains
  `{"Key": "routing.http.xff_header_processing.mode", "Value": "preserve"}`
  on the synthesized template — the regression guard DD-4 requires.
- `npx aws-cdk diff argus-prod-compute` shows only that attribute moving;
  the ECS service, target group, and security groups are unaffected.
- Manual, post-deploy: `aws elbv2 describe-load-balancer-attributes` shows
  `routing.http.xff_header_processing.mode = preserve`; hitting the live API
  through CloudFront from a known public IP and tailing the ECS log group
  shows `ip_address` as that IP, no longer a CloudFront origin-facing
  address; `redis-cli --scan --pattern 'argus:rate-limit:*'`
  shows buckets keyed per viewer rather than shared across a POP.
- No backend rebuild or redeploy is required — the running image behaves
  correctly as soon as the header shape changes.

## Pros and Cons of the Options

### Option 1 — Widen Tomcat's `trustedProxies` to CloudFront's ranges

- Good, because it also requires no change to `RemoteIpValve`'s logic or to
  `BucketResolver` (DD-1).
- Bad, because it requires enumerating AWS's published CloudFront IP ranges
  inside the backend's own configuration — a list AWS rotates without
  notice, with no refresh mechanism in this project (fails DD-3).
- Bad, because getting that range list wrong in either direction either
  reopens the spoofing surface (too wide) or reproduces this exact bug for a
  subset of edge locations (too narrow).

### Option 2 — ALB `preserve` (chosen)

- Good, because it satisfies DD-1 through DD-4 as detailed above.
- Good, because it is a single property on a construct already being
  created, with no new AWS resource and no new dependency.
- Bad, because the correctness of `server.forward-headers-strategy: native`
  now silently depends on an attribute the backend cannot see — mitigated
  only by the CDK assertion (DD-4) and this ADR.

### Option 3 — `CloudFront-Viewer-Address`

- Good, because it depends on nothing but CloudFront itself — correct
  regardless of what the ALB does to `X-Forwarded-For`, and AWS's own
  documented recommendation for this exact topology.
- Bad, because it requires a new origin request policy — the managed
  `AllViewerExceptHostHeader` policy was verified to forward no CloudFront
  headers at all — plus a new backend resolver for `IP:port` / `[IPv6]:port`
  parsing, both of which fail DD-1 and DD-3 relative to Option 2.

## More Information

This decision should be re-evaluated when:

- **CloudFront stops being the only path to the ALB** — the ALB turns
  internet-facing, the security group stops being restricted to the
  CloudFront origin-facing prefix list, or a second origin or direct route
  is added. `preserve` means the ALB writes nothing, so a client that can
  reach the ALB directly would then control the entire header. This is the
  load-bearing precondition behind this decision.
- **A hop is added between CloudFront and Tomcat** (a second load balancer,
  an NLB, a service mesh sidecar, nginx). Any such hop that appends to
  `X-Forwarded-For` reintroduces exactly this bug one layer further in.
- **CloudFront is removed or replaced**, and viewers reach the ALB directly.
  Then nothing appends the viewer IP and `preserve` becomes actively wrong —
  `append` (or Option 1) must come back.
- **A second application is put behind the same ALB** and reads the leftmost
  `X-Forwarded-For` entry instead of the rightmost. The attribute is
  per-load-balancer, not per-target-group.
- **`routing.http.xff_client_port.enabled` is needed.** It cannot be
  combined with `preserve`.
- **NFR-Sec7b's `≤ 600 / min / IP` authenticated bound is implemented**
  (already tracked as open in ADR 0006). More IP-keyed buckets raise the
  blast radius of a wrong IP resolution.
- **Defense in depth independent of this infra property is wanted** —
  switch to `CloudFront-Viewer-Address` (Option 3), which depends on nothing
  but CloudFront itself.

References:

- `#108` — introduced `server.forward-headers-strategy: native` and
  `RemoteIpValve`-based resolution; correct in isolation, incomplete without
  this change.
- [ADR 0006](0006-distributed-rate-limiting.md) — the IP-keyed buckets this
  defect silently widened.
- [AWS: X-Forwarded-For and the Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html) —
  `routing.http.xff_header_processing.mode` semantics.
- [AWS: identifying the real client IP on an ALB behind CloudFront](https://repost.aws/articles/AR-VvhzdKVRgOIYl4SJMTcHg/how-to-identify-the-real-client-ip-on-alb-when-using-cloudfront-as-a-front-end) —
  the `CloudFront-Viewer-Address` recommendation evaluated as Option 3.
- `infra/argus/constructs/backend_service.py`, `infra/tests/test_compute.py`
  — where this decision is implemented and guarded.
- `docs/NFR.md` — NFR-Sec7 (auth rate limits), NFR-Sec7b (authenticated
  default bound).
- MADR 4.0 — <https://adr.github.io/madr/>.
