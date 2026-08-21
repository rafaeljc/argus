# Argus infrastructure

AWS CDK, in Python. See [ARCHITECTURE.md](ARCHITECTURE.md) for the topology.

Infrastructure is deployed **by hand**. No workflow runs `cdk deploy`; the CD
workflows only push artifacts and roll the service.

## Layout

| Path | What it is |
|---|---|
| `app.py` | Entry point: resolves inputs, builds the environment, synthesizes |
| `argus/config.py` | Frozen configuration and sizing constants |
| `argus/naming.py` | Every resource and parameter name |
| `argus/lookups.py` | The only AWS access at synth time |
| `argus/retention.py` | Removal policies, stated explicitly |
| `argus/environment.py` | The five stacks and how they connect |
| `argus/stacks/` | One module per stack |
| `argus/constructs/` | Pieces shared between stacks |
| `scripts/audit-orphans.sh` | Finds resources no stack owns |

## Working locally

```bash
cd infra
python -m venv .venv
.venv/bin/pip install -r requirements.txt -r requirements-dev.txt

.venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy .
.venv/bin/pytest --cov=argus --cov-report=term-missing --cov-fail-under=80
```

`cdk` itself needs the virtualenv on `PATH`, because `cdk.json` runs `python3 app.py`:

```bash
source .venv/bin/activate
npx cdk synth --all
```

`cdk synth` is the real integration test: it type-checks the whole construct
graph against the installed `aws-cdk-lib` and exercises the synth-time lookups
against the account.

## Why two passes

The ECS service cannot be created until an image exists in ECR, and ECR does not
exist until CDK has run. So `argus-prod-foundation` depends on nothing and is
deployed first, an image is pushed by hand, and the rest follows.

## First deployment

### 1. Create the inputs

None of these are created by CDK. `cdk synth` prints the exact command for
anything missing, so running it first is the fastest way to find gaps.

```bash
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name argusapp.click \
  --query 'HostedZones[0].Id' --output text | cut -d/ -f3)

aws ssm put-parameter --name /argus/prod/domain-name    --type String --value argusapp.click
aws ssm put-parameter --name /argus/prod/hosted-zone-id --type String --value "$ZONE_ID"
aws ssm put-parameter --name /argus/prod/alert-email    --type String --value you@example.com
aws ssm put-parameter --name /argus/prod/image-tag      --type String --value "$(git rev-parse HEAD)"

aws ssm put-parameter --name /argus/prod/env/app-base-url                --type String --value https://argusapp.click
aws ssm put-parameter --name /argus/prod/env/web-cors-allowed-origin     --type String --value https://argusapp.click
aws ssm put-parameter --name /argus/prod/env/web-cookie-domain           --type String --value argusapp.click
aws ssm put-parameter --name /argus/prod/env/email-address               --type String --value no-reply@argusapp.click
aws ssm put-parameter --name /argus/prod/env/email-resend-api-url        --type String --value https://api.resend.com
aws ssm put-parameter --name /argus/prod/env/marketdata-massive-api-url  --type String --value <vendor url>

aws secretsmanager create-secret --name argus/prod/vendor-keys --secret-string '{
  "email-resend-api-key": "<key>",
  "marketdata-massive-api-key": "<key>"
}'
```

`/argus/prod/env/*` values are hand-managed for good. CDK references them and
never writes them, so a deploy cannot clobber one.

### 2. Pass one

```bash
npx cdk bootstrap          # once per account and region
npx cdk deploy argus-prod-foundation
```

Then set the repository variables from the roles it created:

| Variable | Value |
|---|---|
| `AWS_REGION` | `us-east-1` |
| `BACKEND_DEPLOY_ROLE_ARN` | `arn:aws:iam::<account>:role/argus-prod-cd-backend` |
| `FRONTEND_DEPLOY_ROLE_ARN` | `arn:aws:iam::<account>:role/argus-prod-cd-frontend` |
| `VITE_API_BASE_URL` | `/api/v1` |

Running CD Backend now is a useful check: it should fail on
`ParameterNotFound` for `/argus/prod/out/backend/cluster-name` rather than doing
anything half-way.

### 3. Push the first image by hand

The real backend image, not a placeholder, so pass two brings up a working
service.

```bash
SHA=$(git rev-parse HEAD)
URI=$(aws ssm get-parameter --name /argus/prod/out/backend/repository-uri \
  --query Parameter.Value --output text)

aws ecr get-login-password | docker login --username AWS --password-stdin "${URI%%/*}"
docker build -t "$URI:$SHA" backend/
docker push "$URI:$SHA"

aws ssm put-parameter --name /argus/prod/image-tag --value "$SHA" --overwrite
```

### 4. Pass two

```bash
npx cdk diff --all         # always, before deploying
npx cdk deploy --all
```

Confirm the ECS service reaches steady state, then run CD Backend and CD
Frontend. Finish with `./scripts/audit-orphans.sh`, which should print nothing.

## Routine deployments

Nothing manual. CD Backend builds and rolls the service on a push to `backend/`;
CD Frontend uploads and invalidates on a push to `frontend/`. Both find what
they need through `/argus/prod/out/*`, and both fail loudly if it is absent.

To roll back to an older image, set `/argus/prod/image-tag` to that SHA and
`cdk deploy argus-prod-compute`.

## Changing the infrastructure

Run `npx cdk diff --all` first, every time. Two things in the output are a
stop-and-think:

* `requires replacement` on any resource in the table below
* any `[-]` line removing one of them

Those four resources are retained, so CloudFormation will not delete them --
which also means a replacement would leave the original behind. They each carry
an explicit physical name so that a forced replacement fails on a name collision
instead of silently orphaning the original.

| Resource | Name | Why it is retained |
|---|---|---|
| RDS instance | `argus-prod-db` | Holds data that cannot be rebuilt |
| Database secret | `argus/prod/db` | Losing it locks everyone out of the instance |
| Frontend bucket | `argus-prod-frontend-<account>` | Referenced by CloudFront as an origin |
| ECR repository | `argus-backend` | Deleting it strands every deployed tag |

`tests/test_retained_resources.py` pins that list by logical id. If it fails,
either a resource just became orphanable or a retained one is about to be
replaced. Update `tests/retained_resources.json` only after deciding that is
what you want.

## Teardown

`cdk destroy` leaves the four retained resources behind, plus the RDS final
snapshot. That is by design, and it means teardown is not free:

```bash
aws rds delete-db-instance --db-instance-identifier argus-prod-db \
  --skip-final-snapshot --delete-automated-backups
aws s3 rb "s3://argus-prod-frontend-<account>" --force
aws ecr delete-repository --repository-name argus-backend --force
aws secretsmanager delete-secret --secret-id argus/prod/db \
  --force-delete-without-recovery
```

The database also has `deletion_protection`, which has to be turned off before
it will delete at all.

Deleting a secret without `--force-delete-without-recovery` schedules it and
holds the name for the recovery window, so recreating one under the same name
fails until the window elapses.

Running `./scripts/audit-orphans.sh` after a teardown will report these. That is
expected.
