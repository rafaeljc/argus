"""The running backend.

Deployed in the second pass, because the task definition names an image that has
to already exist in ECR. Until it does, this stack cannot be created -- which is
why the first pass stops at the foundation.
"""

from aws_cdk import Duration
from aws_cdk import aws_cloudwatch as cloudwatch
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_elasticloadbalancingv2 as elbv2
from aws_cdk import aws_iam as iam
from aws_cdk import aws_rds as rds
from aws_cdk import aws_secretsmanager as secretsmanager
from constructs import Construct

from argus.config import DATABASE, EnvironmentConfig
from argus.constructs.alarms import CriticalAlarms
from argus.constructs.backend_service import CONTAINER_NAME, BackendService, BackendServiceProps
from argus.stacks.base import ArgusStack

HEALTHY_HOSTS_ALARM_COUNT = 1
SERVER_ERRORS_ALARM_COUNT = 10


class ComputeStack(ArgusStack):
    """Load balancer, cluster and service, plus the access the deploy role needs."""

    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        config: EnvironmentConfig,
        vpc: ec2.IVpc,
        database: rds.IDatabaseInstance,
        connection_secret: secretsmanager.ISecret,
    ) -> None:
        super().__init__(scope, construct_id, config=config)

        self.backend = BackendService(
            self,
            "Backend",
            props=BackendServiceProps(config=config, vpc=vpc, connection_secret=connection_secret),
        )

        self.backend.allow_ingress_from(ec2.Peer.prefix_list(config.cloudfront_prefix_list_id))
        self._allow_the_task_to_reach_the_database(database)
        self._allow_the_deploy_role_to_roll_the_service(config)
        self._alarm_on_a_service_that_stops_serving()
        self._publish_discovery()

    def _allow_the_task_to_reach_the_database(self, database: rds.IDatabaseInstance) -> None:
        """Open 5432 from this stack rather than the data stack.

        The rule has to be created in the scope that already depends on the
        other: this stack reads the database's secret, so adding the rule in the
        data stack instead would make the two depend on each other and fail
        synthesis with a cyclic reference.
        """
        database_security_group = ec2.SecurityGroup.from_security_group_id(
            self,
            "DatabaseSecurityGroup",
            database.connections.security_groups[0].security_group_id,
        )
        database_security_group.add_ingress_rule(
            peer=self.backend.service.connections.security_groups[0],
            connection=ec2.Port.tcp(DATABASE.port),
            description="Backend task",
        )

    def _allow_the_deploy_role_to_roll_the_service(self, config: EnvironmentConfig) -> None:
        role = iam.Role.from_role_name(
            self, "BackendDeployRole", config.naming.resource("cd", "backend")
        )
        role.add_to_principal_policy(
            iam.PolicyStatement(
                actions=[
                    "ecs:DescribeServices",
                    "ecs:UpdateService",
                    "ecs:DescribeTaskDefinition",
                ],
                resources=[self.backend.service.service_arn],
            )
        )
        # Task definitions are not scoped to a resource by ECS, so these cannot
        # be narrowed further. Listing and deregistering are how the workflow
        # prunes the revisions it accumulates: every deploy registers one and
        # AWS never reaps them.
        role.add_to_principal_policy(
            iam.PolicyStatement(
                actions=[
                    "ecs:RegisterTaskDefinition",
                    "ecs:DescribeTaskDefinition",
                    "ecs:ListTaskDefinitions",
                    "ecs:DeregisterTaskDefinition",
                ],
                resources=["*"],
            )
        )
        # Handing a task definition to ECS means passing the roles it names.
        task_definition = self.backend.task_definition
        passable = [task_definition.task_role.role_arn]
        if task_definition.execution_role is not None:
            passable.append(task_definition.execution_role.role_arn)
        role.add_to_principal_policy(
            iam.PolicyStatement(actions=["iam:PassRole"], resources=passable)
        )

    def _alarm_on_a_service_that_stops_serving(self) -> None:
        alarms = CriticalAlarms(self, self.naming)
        load_balancer = self.backend.load_balancer
        alarms.add(
            "backend-unavailable",
            # Healthy count rather than unhealthy: a task that has crashed and
            # been deregistered is not unhealthy, it is absent, and absent is
            # the outage.
            metric=self.backend.target_group.metrics.healthy_host_count(
                statistic="Minimum", period=Duration.minutes(1)
            ),
            threshold=HEALTHY_HOSTS_ALARM_COUNT,
            comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
            # A deploy stops the old task before starting the new one, so a
            # brief zero is normal; several minutes of it is not.
            evaluation_periods=5,
            treat_missing_data=cloudwatch.TreatMissingData.BREACHING,
            description="The load balancer has no healthy backend task to send requests to.",
        )
        alarms.add(
            "backend-erroring",
            metric=load_balancer.metrics.http_code_target(
                elbv2.HttpCodeTarget.TARGET_5XX_COUNT,
                statistic="Sum",
                period=Duration.minutes(5),
            ),
            threshold=SERVER_ERRORS_ALARM_COUNT,
            description="The backend is returning server errors.",
        )

    def _publish_discovery(self) -> None:
        discovery = self.discovery
        discovery.publish(
            "backend",
            "cluster-name",
            self.backend.cluster.cluster_name,
            "ECS cluster the backend service runs in",
        )
        discovery.publish(
            "backend",
            "service-name",
            self.backend.service.service_name,
            "ECS service to roll when a new image is deployed",
        )
        discovery.publish(
            "backend",
            "task-definition-family",
            self.backend.task_definition.family,
            "Task definition family to register new revisions against",
        )
        discovery.publish(
            "backend",
            "container-name",
            CONTAINER_NAME,
            "Container within the task definition that carries the image",
        )
