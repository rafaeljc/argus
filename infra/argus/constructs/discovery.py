"""How deployment workflows find the resources they act on.

The workflows must not name CloudFormation stacks: a stack rename would break
them silently, and a stack is an implementation detail of how this project is
organised. Instead each stack publishes the handful of values its workflow needs
to SSM, and the workflow reads exactly one prefix.

Paths are keyed by component -- ``out/backend``, ``out/frontend`` -- not by AWS
service, so the choice between, say, ECS and something else never leaks into the
contract.
"""

from dataclasses import dataclass

from aws_cdk import RemovalPolicy
from aws_cdk import aws_ssm as ssm
from constructs import Construct

from argus.naming import Naming


@dataclass(frozen=True)
class Discovery:
    """Publishes discovery values for one stack."""

    scope: Construct
    naming: Naming

    def publish(self, component: str, name: str, value: str, description: str) -> None:
        parameter = ssm.StringParameter(
            self.scope,
            _construct_id(component, name),
            parameter_name=self.naming.output_parameter(component, name),
            string_value=value,
            description=description,
        )
        # Rebuilt on every deploy; a leftover would point at a resource that no
        # longer exists, which is worse than no parameter at all.
        parameter.apply_removal_policy(RemovalPolicy.DESTROY)


def _construct_id(component: str, name: str) -> str:
    words = f"{component}-{name}".split("-")
    return "".join(word.capitalize() for word in words) + "Output"
