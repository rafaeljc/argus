from aws_cdk import Stack
from constructs import Construct

from argus_infra.config import ArgusEnv
from argus_infra.constructs.database import Database
from argus_infra.constructs.network import Network
from argus_infra.constructs.registry import Registry


class ArgusStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, *, env_config: ArgusEnv, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        image_tag = self.node.try_get_context("image_tag")
        if not image_tag:
            raise ValueError("image_tag context is required: cdk deploy -c image_tag=<tag>")

        self.env_config = env_config
        self.image_tag = image_tag

        self.network = Network(self, "Network")
        self.registry = Registry(self, "Registry")
        self.database = Database(
            self,
            "Database",
            vpc=self.network.vpc,
            instance_class=env_config.db_instance_class,
        )
