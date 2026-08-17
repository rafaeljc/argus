from aws_cdk import aws_ecr as ecr
from constructs import Construct


class Registry(Construct):
    def __init__(self, scope: Construct, construct_id: str) -> None:
        super().__init__(scope, construct_id)

        self.repository = ecr.Repository(
            self,
            "Backend",
            image_tag_mutability=ecr.TagMutability.IMMUTABLE,
            lifecycle_rules=[ecr.LifecycleRule(max_image_count=10)],
        )
