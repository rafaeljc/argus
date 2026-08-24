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

`cdk.json` runs `.venv/bin/python app.py`, so the CDK CLI works without
activating anything — but it does mean the virtualenv must live at `infra/.venv`
exactly as created above.

```bash
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
aws ssm put-parameter --name /argus/prod/env/admin-user-id               --type String --value none

aws secretsmanager create-secret --name argus/prod/vendor-keys --secret-string '{
  "email-resend-api-key": "<key>",
  "marketdata-massive-api-key": "<key>"
}'
```

`/argus/prod/env/*` values are hand-managed for good. CDK references them and
never writes them, so a deploy cannot clobber one.

Every one of them has to exist before the first deploy that references it: ECS
resolves each at task start and fails the task with `ResourceInitializationError`
if one is missing. `admin-user-id` has no real value yet at this point and SSM
rejects an empty `String`, hence the `none` sentinel -- see
[Appointing the admin](#appointing-the-admin).

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

## Appointing the admin

`/argus/prod/env/admin-user-id` names the single administrator. The application
promotes that account on startup and demotes every other admin in the same
statement, so the parameter -- not the database -- is the source of truth for who
holds the flag.

No account is created and no password is stored. The admin is an ordinary user
who signed up and verified through the app, so nothing here mints credentials.

1. Sign up through the app and complete email verification, as a normal user.
2. Take the account's UUID from the signup response (`data.user_id`), from
   `GET /api/v1/account` once logged in, or from the `auth.signup` audit line in
   CloudWatch. None of these need admin access.
3. Point the parameter at it:
   ```bash
   aws ssm put-parameter --name /argus/prod/env/admin-user-id --value <uuid> --overwrite
   ```
4. `aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment`.
   The value is read at task start, so a restart is required either way.
5. Log in. The admin surface is reachable.

The account has to be active, verified and unsuspended, or the promotion is
refused and logged. Moving the admin to someone else is the same operation:
change the value and restart, and the previous admin is demoted by the same
statement. Re-running with the value unchanged does nothing.

Setting it back to `none` leaves the current assignment alone rather than
clearing it -- a value that fails to resolve must never silently strip the
running deployment of its administrator. To remove someone, appoint their
successor.

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

`cdk destroy` leaves the four retained resources behind. That is by design, and
it means teardown is not free -- and not a single command.

**The database has to be deleted before `cdk destroy`, not after.** The subnet
group and security group around it are disposable, so CloudFormation deletes
them; the instance is retained, so it does not. Destroying the data stack while
the instance still exists therefore asks CloudFormation to delete a subnet group
the instance is still using, which fails:

```
DELETE_SKIPPED  AWS::RDS::DBInstance     (retained, as designed)
DELETE_FAILED   AWS::RDS::DBSubnetGroup  ...instance argus-prod-db is still using it
```

The network stack fails the same way on its subnets. Deleting the instance first
avoids both.

### 1. Delete the database

```bash
aws rds modify-db-instance --db-instance-identifier argus-prod-db \
  --no-deletion-protection --apply-immediately
aws rds delete-db-instance --db-instance-identifier argus-prod-db \
  --skip-final-snapshot --delete-automated-backups
aws rds wait db-instance-deleted --db-instance-identifier argus-prod-db
```

`deletion_protection` has to come off first or the delete is refused. Wait for it
to finish -- an instance still in `deleting` blocks the stack just as an
available one does. Drop `--skip-final-snapshot` to keep a restorable copy, and
remember the snapshot then outlives everything else here.

### 2. Destroy the stacks

```bash
npx cdk destroy --all
```

### 3. Delete what was retained

```bash
aws s3 rb "s3://argus-prod-frontend-<account>" --force
aws ecr delete-repository --repository-name argus-backend --force
aws secretsmanager delete-secret --secret-id argus/prod/db \
  --force-delete-without-recovery
```

Without `--force-delete-without-recovery` a secret is only scheduled for
deletion and holds its name for the recovery window, so recreating one under the
same name fails until the window elapses.

### 4. Delete the hand-managed inputs

Not created by CDK, so nothing above removes them:

```bash
aws secretsmanager delete-secret --secret-id argus/prod/vendor-keys \
  --force-delete-without-recovery
for name in $(aws ssm get-parameters-by-path --path /argus --recursive \
    --query 'Parameters[].Name' --output text); do
  aws ssm delete-parameter --name "$name"
done
```

The Route 53 hosted zone is adopted, not created, so it survives -- only the
apex record goes with the edge stack. Leave the zone alone unless the domain
itself is being retired; it also holds the mail records.

Running `./scripts/audit-orphans.sh` between steps 2 and 3 will report the
retained resources. That is expected.
