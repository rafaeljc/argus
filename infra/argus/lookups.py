"""The only boundary to AWS at synth time.

Every value the stacks need from outside this repository is read here, through a
narrow :class:`Lookups` protocol, so tests inject a fake and never touch AWS.

Deliberately absent: CDK context lookups. Nothing in this project caches a
lookup into ``cdk.context.json``, so a synth always reflects the account as it is
now rather than as it was when someone last committed a context file.

A bad bootstrap should fail in seconds at synth, not thirty minutes into a
CloudFormation rollback -- so :func:`resolve_inputs` gathers *every* missing
input and reports them together, each with the command that fixes it.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:  # pragma: no cover - import-time only
    from mypy_boto3_ec2.client import EC2Client
    from mypy_boto3_ssm.client import SSMClient

    from argus.naming import Naming

CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST = "com.amazonaws.global.cloudfront.origin-facing"


class Lookups(Protocol):
    """Reads values that live in the AWS account rather than in this repository."""

    def ssm_parameter(self, name: str) -> str | None:
        """The parameter's value, or ``None`` if it does not exist."""

    def managed_prefix_list_id(self, name: str) -> str | None:
        """The prefix list's id, or ``None`` if no such list exists here."""


@dataclass(frozen=True)
class SynthInputs:
    """Values resolved from AWS before any construct is created."""

    domain_name: str
    hosted_zone_id: str
    image_tag: str
    alert_email: str
    cloudfront_prefix_list_id: str


@dataclass(frozen=True)
class MissingInput:
    """One thing that has to exist before this project can be synthesized."""

    subject: str
    purpose: str
    remediation: str


class MissingInputsError(RuntimeError):
    """Raised once, listing everything that is missing."""

    def __init__(self, missing: Sequence[MissingInput]) -> None:
        self.missing = tuple(missing)
        super().__init__(_describe(self.missing))


def _describe(missing: Sequence[MissingInput]) -> str:
    entries = "\n".join(
        f"\n  {item.subject}\n      {item.purpose}\n      fix: {item.remediation}"
        for item in missing
    )
    return f"Cannot synthesize: {len(missing)} required input(s) missing.\n{entries}\n"


def resolve_inputs(lookups: Lookups, naming: Naming) -> SynthInputs:
    """Read every synth-time input, or raise naming all of the ones that are absent."""
    reader = _InputReader(lookups, naming)
    inputs = SynthInputs(
        domain_name=reader.parameter("domain-name", "public domain name of the site"),
        hosted_zone_id=reader.parameter(
            "hosted-zone-id", "id of the existing Route 53 public hosted zone"
        ),
        image_tag=reader.parameter("image-tag", "commit sha of the backend image to run"),
        alert_email=reader.parameter("alert-email", "recipient of critical alarm notifications"),
        cloudfront_prefix_list_id=reader.cloudfront_prefix_list(),
    )
    reader.raise_if_incomplete()
    return inputs


@dataclass
class _InputReader:
    """Collects missing inputs instead of failing on the first one."""

    lookups: Lookups
    naming: Naming
    missing: list[MissingInput] = field(default_factory=list)

    def parameter(self, name: str, purpose: str) -> str:
        path = self.naming.input_parameter(name)
        value = self.lookups.ssm_parameter(path)
        if value is None or not value.strip():
            self.missing.append(
                MissingInput(
                    subject=f"SSM parameter {path}",
                    purpose=purpose,
                    remediation=(
                        f"aws ssm put-parameter --name {path} --type String --value <value>"
                    ),
                )
            )
            return ""
        return value.strip()

    def cloudfront_prefix_list(self) -> str:
        name = CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST
        value = self.lookups.managed_prefix_list_id(name)
        if value is None:
            self.missing.append(
                MissingInput(
                    subject=f"managed prefix list {name}",
                    purpose="restricts the load balancer to CloudFront origin traffic",
                    remediation=(
                        "this list is published by AWS -- check the region, and that the "
                        "caller may run ec2:DescribeManagedPrefixLists"
                    ),
                )
            )
            return ""
        return value

    def raise_if_incomplete(self) -> None:
        if self.missing:
            raise MissingInputsError(self.missing)


class AwsLookups:
    """Reads inputs from the live account. Clients are created on first use."""

    def __init__(self, ssm: SSMClient | None = None, ec2: EC2Client | None = None) -> None:
        self._ssm = ssm
        self._ec2 = ec2

    def ssm_parameter(self, name: str) -> str | None:
        client = self._ssm_client()
        try:
            response = client.get_parameter(Name=name, WithDecryption=True)
        except client.exceptions.ParameterNotFound:
            return None
        return response["Parameter"]["Value"]

    def managed_prefix_list_id(self, name: str) -> str | None:
        response = self._ec2_client().describe_managed_prefix_lists(
            Filters=[{"Name": "prefix-list-name", "Values": [name]}]
        )
        prefix_lists = response["PrefixLists"]
        return prefix_lists[0]["PrefixListId"] if prefix_lists else None

    def _ssm_client(self) -> SSMClient:
        if self._ssm is None:
            import boto3

            self._ssm = boto3.client("ssm")
        return self._ssm

    def _ec2_client(self) -> EC2Client:
        if self._ec2 is None:
            import boto3

            self._ec2 = boto3.client("ec2")
        return self._ec2
