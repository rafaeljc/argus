"""Removal policies, stated explicitly.

CDK applies ``RETAIN`` implicitly to several stateful constructs. A resource left
on its library default is a resource nobody decided about -- and a retained
resource survives both stack deletion *and* replacement, which is how orphans
happen. Every stateful resource in this project therefore names its durability
at the point of construction, using this enum rather than ``RemovalPolicy``
directly, so the reason is visible next to the choice.
"""

from enum import Enum

from aws_cdk import RemovalPolicy


class Durability(Enum):
    """Whether destroying a resource destroys something unrecoverable."""

    # Holds data that cannot be rebuilt. Outlives its stack, deliberately.
    RETAINED = RemovalPolicy.RETAIN

    # Rebuildable from this repository. Must never linger.
    DISPOSABLE = RemovalPolicy.DESTROY

    @property
    def removal_policy(self) -> RemovalPolicy:
        policy: RemovalPolicy = self.value
        return policy
