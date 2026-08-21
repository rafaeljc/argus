"""What every stack in this project shares."""

from aws_cdk import Environment, Stack
from constructs import Construct

from argus.config import EnvironmentConfig
from argus.constructs.discovery import Discovery
from argus.naming import Naming


class ArgusStack(Stack):
    """A stack pinned to one environment's account and region.

    Pinning matters: an environment-agnostic stack resolves its account at
    deploy time from whichever credentials happen to be in the shell, which is
    the wrong way to find out you were pointed at the wrong account.
    """

    def __init__(self, scope: Construct, construct_id: str, *, config: EnvironmentConfig) -> None:
        super().__init__(
            scope,
            construct_id,
            env=Environment(account=config.account, region=config.region),
        )
        self.config = config

    @property
    def naming(self) -> Naming:
        return self.config.naming

    @property
    def discovery(self) -> Discovery:
        return Discovery(self, self.naming)
