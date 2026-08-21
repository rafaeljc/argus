#!/usr/bin/env bash
#
# Lists resources the account believes belong to this project but that no stack
# actually contains -- that is, resources an update or a teardown left behind.
#
# Every construct is tagged argus:managed-by=cdk, so the account can be asked
# what it thinks is ours and the answer compared against what the stacks say
# they own. Expected output is nothing.
#
# Two caveats worth knowing before trusting a clean run:
#
#   * The tagging API only reports resource types that support tags. Untagged
#     types (subnet route associations, security group rules, task definition
#     revisions) are invisible to it.
#   * Matching is by physical id appearing in the ARN, because CloudFormation
#     reports physical ids and the tagging API reports ARNs, and the two are not
#     the same string for every resource type.
#   * The tagging index lags deletion, so a resource can be reported minutes to
#     hours after it is gone. Confirm a reported ARN still exists before acting
#     on it -- the last section of the output says how.
#
# Task definition revisions are skipped. Every deploy registers one and only the
# newest is ever owned by the stack, so by construction the rest are unowned;
# they are pruned by the backend deployment workflow, not here.
#
# After a deliberate teardown this will report the four retained resources.
# That is expected, not a defect -- see the teardown section of README.md.

set -euo pipefail

ENVIRONMENT="${ARGUS_ENVIRONMENT:-prod}"
STACKS=(
    "argus-${ENVIRONMENT}-foundation"
    "argus-${ENVIRONMENT}-network"
    "argus-${ENVIRONMENT}-data"
    "argus-${ENVIRONMENT}-compute"
    "argus-${ENVIRONMENT}-edge"
)

echo "Auditing environment '${ENVIRONMENT}'..." >&2

owned=$(mktemp)
trap 'rm -f "$owned"' EXIT

for stack in "${STACKS[@]}"; do
    if ! aws cloudformation describe-stacks --stack-name "$stack" >/dev/null 2>&1; then
        echo "  stack $stack does not exist, skipping" >&2
        continue
    fi
    aws cloudformation list-stack-resources --stack-name "$stack" \
        --query 'StackResourceSummaries[].PhysicalResourceId' --output text \
        | tr '\t' '\n' >>"$owned"
    echo "  read $stack" >&2
done

tagged=$(aws resourcegroupstaggingapi get-resources \
    --tag-filters "Key=argus:managed-by,Values=cdk" \
    --query 'ResourceTagMappingList[].ResourceARN' --output text | tr '\t' '\n')

orphans=0
while IFS= read -r arn; do
    [ -n "$arn" ] || continue
    case "$arn" in
        *:task-definition/*) continue ;;
    esac
    if ! grep -qF -f <(grep -v '^$' "$owned") <<<"$arn" 2>/dev/null; then
        echo "ORPHAN: $arn"
        orphans=$((orphans + 1))
    fi
done <<<"$tagged"

if [ "$orphans" -eq 0 ]; then
    echo "No orphans found." >&2
    exit 0
fi

cat >&2 <<EOF
${orphans} resource(s) tagged as ours but owned by no stack.

Confirm each one still exists before acting -- the tagging index lags deletion:
    aws <service> describe-<resource> ...   # NoSuchEntity means it is already gone
EOF
exit 1
