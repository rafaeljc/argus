"""Everything that must exist before an image can be built.

This is the only stack deployed in the first pass. The ECS service cannot be
created until an image exists in ECR, and ECR does not exist until CDK has run,
so the chicken-and-egg is broken by keeping this stack free of any dependency on
the application: no VPC, no compute, and therefore no cost beyond storage.
"""

from aws_cdk import Duration
from aws_cdk import aws_ecr as ecr
from aws_cdk import aws_iam as iam
from aws_cdk import aws_sns as sns
from aws_cdk import aws_sns_subscriptions as subscriptions
from constructs import Construct

from argus.config import EnvironmentConfig
from argus.constructs.github_deploy_roles import GitHubDeployRoles
from argus.naming import BACKEND_REPOSITORY_NAME
from argus.retention import Durability
from argus.stacks.base import ArgusStack

UNTAGGED_IMAGE_LIFETIME = Duration.days(7)
TAGGED_IMAGES_KEPT = 20


class FoundationStack(ArgusStack):
    """Image repository, deployment identities, and alarm delivery."""

    def __init__(self, scope: Construct, construct_id: str, *, config: EnvironmentConfig) -> None:
        super().__init__(scope, construct_id, config=config)

        self.repository = self._image_repository()
        self.alarm_topic = self._alarm_topic(config)
        self.deploy_roles = GitHubDeployRoles(self, "DeployRoles", config=config)

        self._allow_backend_deployments(config)
        self._allow_reading_discovery(config)

        discovery = self.discovery
        discovery.publish(
            "backend",
            "repository-name",
            self.repository.repository_name,
            "ECR repository holding backend images",
        )
        discovery.publish(
            "backend",
            "repository-uri",
            self.repository.repository_uri,
            "Registry path to tag and push backend images to",
        )

    def _image_repository(self) -> ecr.Repository:
        return ecr.Repository(
            self,
            "BackendRepository",
            repository_name=BACKEND_REPOSITORY_NAME,
            image_scan_on_push=True,
            # Retained, and named explicitly: deleting this repository would
            # strand every deployed tag, and an explicit name turns an
            # accidental replacement into a name collision that fails the
            # deploy rather than orphaning the original.
            removal_policy=Durability.RETAINED.removal_policy,
            empty_on_delete=False,
            lifecycle_rules=[
                # Untagged images are build leftovers, not deployable artifacts.
                ecr.LifecycleRule(
                    rule_priority=1,
                    tag_status=ecr.TagStatus.UNTAGGED,
                    max_image_age=UNTAGGED_IMAGE_LIFETIME,
                    description="Expire untagged images",
                ),
                # Images accumulate one per commit forever; keep enough history
                # to roll back to, and no more.
                ecr.LifecycleRule(
                    rule_priority=2,
                    tag_status=ecr.TagStatus.TAGGED,
                    tag_pattern_list=["*"],
                    max_image_count=TAGGED_IMAGES_KEPT,
                    description="Keep recent tagged images",
                ),
            ],
        )

    def _alarm_topic(self, config: EnvironmentConfig) -> sns.Topic:
        topic = sns.Topic(
            self,
            "AlarmTopic",
            topic_name=config.naming.resource("alarms"),
            display_name=f"Argus {config.name} critical alarms",
        )
        topic.add_subscription(subscriptions.EmailSubscription(config.alert_email))
        return topic

    def _allow_reading_discovery(self, config: EnvironmentConfig) -> None:
        """Let each role read the parameters its workflow finds its resources through.

        Granted per component prefix rather than per parameter, because a later
        stack publishing a new value under the same prefix must not require the
        foundation stack to be redeployed before the workflow can read it.
        """
        for component, role in (
            ("backend", self.deploy_roles.backend),
            ("frontend", self.deploy_roles.frontend),
        ):
            role.add_to_policy(
                iam.PolicyStatement(
                    actions=["ssm:GetParameter"],
                    resources=[
                        self.format_arn(
                            service="ssm",
                            resource="parameter",
                            resource_name=config.naming.output_parameter(component, "*")[1:],
                        )
                    ],
                )
            )

    def _allow_backend_deployments(self, config: EnvironmentConfig) -> None:
        """Grant the backend role the two things this stack owns: push, and record the tag."""
        self.repository.grant_pull_push(self.deploy_roles.backend)

        image_tag = config.naming.input_parameter("image-tag")
        self.deploy_roles.backend.add_to_policy(
            iam.PolicyStatement(
                actions=["ssm:PutParameter"],
                resources=[
                    self.format_arn(
                        service="ssm", resource="parameter", resource_name=image_tag[1:]
                    )
                ],
            )
        )
