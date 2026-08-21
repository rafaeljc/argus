"""The backend running behind an internal load balancer.

The contract with the application is the three tables below: every value the
backend reads from its environment arrives as an ECS *secret*, resolved by the
agent at task start. Nothing configurable is baked into the image, and nothing
sensitive appears in the task definition.

The database url is assembled by the application from five separate fields
because ECS injects each field of a secret as its own variable and cannot
concatenate them.
"""

from dataclasses import dataclass

from aws_cdk import Duration
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_ecr as ecr
from aws_cdk import aws_ecs as ecs
from aws_cdk import aws_elasticloadbalancingv2 as elbv2
from aws_cdk import aws_logs as logs
from aws_cdk import aws_secretsmanager as secretsmanager
from aws_cdk import aws_ssm as ssm
from constructs import Construct

from argus.config import BACKEND, EnvironmentConfig
from argus.naming import BACKEND_REPOSITORY_NAME
from argus.retention import Durability

CONTAINER_NAME = "backend"
LISTENER_PORT = 80

# Hand-managed parameters under /argus/prod/env, keyed by the variable the
# application reads.
ENVIRONMENT_PARAMETERS = {
    "ARGUS_APP_BASE_URL": "app-base-url",
    "ARGUS_WEB_CORS_ALLOWED_ORIGIN": "web-cors-allowed-origin",
    "ARGUS_WEB_COOKIE_DOMAIN": "web-cookie-domain",
    "ARGUS_EMAIL_ADDRESS": "email-address",
    "ARGUS_EMAIL_RESEND_API_URL": "email-resend-api-url",
    "ARGUS_MARKETDATA_MASSIVE_API_URL": "marketdata-massive-api-url",
}

# Fields of the hand-created argus/<env>/vendor-keys secret.
VENDOR_KEY_FIELDS = {
    "ARGUS_EMAIL_RESEND_API_KEY": "email-resend-api-key",
    "ARGUS_MARKETDATA_MASSIVE_API_KEY": "marketdata-massive-api-key",
}

# Fields of the secret RDS generates, filled in once it is attached.
DATABASE_FIELDS = {
    "ARGUS_DB_HOST": "host",
    "ARGUS_DB_PORT": "port",
    "ARGUS_DB_NAME": "dbname",
    "ARGUS_DB_USERNAME": "username",
    "ARGUS_DB_PASSWORD": "password",
}


@dataclass(frozen=True)
class BackendServiceProps:
    """Everything the service needs from the stacks around it."""

    config: EnvironmentConfig
    vpc: ec2.IVpc
    connection_secret: secretsmanager.ISecret


class BackendService(Construct):
    """Load balancer, cluster, task definition and service for the backend."""

    def __init__(self, scope: Construct, construct_id: str, *, props: BackendServiceProps) -> None:
        super().__init__(scope, construct_id)
        self._props = props
        config = props.config

        self.load_balancer = elbv2.ApplicationLoadBalancer(
            self,
            "LoadBalancer",
            vpc=props.vpc,
            # Reachable only through CloudFront's VPC origin; nothing here ever
            # answers a request straight off the internet.
            internet_facing=False,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS),
        )
        self.cluster = ecs.Cluster(
            self,
            "Cluster",
            vpc=props.vpc,
            cluster_name=config.naming.resource("cluster"),
        )
        self.task_definition = self._task_definition(config)
        self.service = self._service(config)

        self._route_traffic()
        self._allow_load_balancer_to_reach_health_port()

    def _task_definition(self, config: EnvironmentConfig) -> ecs.FargateTaskDefinition:
        task_definition = ecs.FargateTaskDefinition(
            self,
            "TaskDefinition",
            family=config.naming.resource(CONTAINER_NAME),
            cpu=BACKEND.cpu,
            memory_limit_mib=BACKEND.memory_mib,
        )
        repository = ecr.Repository.from_repository_name(
            self, "BackendRepository", BACKEND_REPOSITORY_NAME
        )
        container = task_definition.add_container(
            CONTAINER_NAME,
            container_name=CONTAINER_NAME,
            image=ecs.ContainerImage.from_ecr_repository(repository, config.image_tag),
            environment={"SPRING_PROFILES_ACTIVE": config.name},
            secrets=self._secrets(config),
            logging=ecs.LogDrivers.aws_logs(
                stream_prefix=CONTAINER_NAME,
                log_group=logs.LogGroup(
                    self,
                    "LogGroup",
                    retention=logs.RetentionDays.ONE_MONTH,
                    removal_policy=Durability.DISPOSABLE.removal_policy,
                ),
            ),
        )
        container.add_port_mappings(
            ecs.PortMapping(container_port=BACKEND.traffic_port),
            ecs.PortMapping(container_port=BACKEND.management_port),
        )
        return task_definition

    def _secrets(self, config: EnvironmentConfig) -> dict[str, ecs.Secret]:
        vendor_keys = secretsmanager.Secret.from_secret_name_v2(
            self, "VendorKeys", f"argus/{config.name}/vendor-keys"
        )
        secrets = {
            variable: ecs.Secret.from_ssm_parameter(
                ssm.StringParameter.from_string_parameter_name(
                    self, f"{variable}Parameter", config.naming.input_parameter("env", parameter)
                )
            )
            for variable, parameter in ENVIRONMENT_PARAMETERS.items()
        }
        secrets.update(
            {
                variable: ecs.Secret.from_secrets_manager(vendor_keys, field)
                for variable, field in VENDOR_KEY_FIELDS.items()
            }
        )
        secrets.update(
            {
                variable: ecs.Secret.from_secrets_manager(self._props.connection_secret, field)
                for variable, field in DATABASE_FIELDS.items()
            }
        )
        return secrets

    def _service(self, config: EnvironmentConfig) -> ecs.FargateService:
        return ecs.FargateService(
            self,
            "Service",
            cluster=self.cluster,
            task_definition=self.task_definition,
            service_name=config.naming.resource(CONTAINER_NAME),
            assign_public_ip=False,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS),
            circuit_breaker=ecs.DeploymentCircuitBreaker(enable=True, rollback=True),
            # Stop the old task before starting the new one: a single task, so
            # allowing two would double the bill during every deploy. The cost
            # is a few seconds with nothing serving.
            min_healthy_percent=0,
            max_healthy_percent=100,
            # desired_count is deliberately absent. Setting it writes a
            # DesiredCount into the template, and every deploy would then revert
            # whatever the running count had been changed to.
        )

    def _route_traffic(self) -> None:
        listener = self.load_balancer.add_listener(
            "Listener", port=LISTENER_PORT, open=False, protocol=elbv2.ApplicationProtocol.HTTP
        )
        self.target_group = listener.add_targets(
            "Backend",
            port=BACKEND.traffic_port,
            protocol=elbv2.ApplicationProtocol.HTTP,
            targets=[self.service],
            health_check=elbv2.HealthCheck(
                # Answered on the management port, and excludes the vendor
                # breakers: a vendor outage degrades a background capability
                # and must not take every task out of service at once.
                port=str(BACKEND.management_port),
                path=BACKEND.readiness_path,
                interval=Duration.seconds(30),
                timeout=Duration.seconds(5),
                healthy_threshold_count=2,
                unhealthy_threshold_count=3,
            ),
            deregistration_delay=Duration.seconds(15),
        )

    def _allow_load_balancer_to_reach_health_port(self) -> None:
        # add_targets opens the traffic port only.
        self.service.connections.allow_from(
            self.load_balancer,
            ec2.Port.tcp(BACKEND.management_port),
            "Health check",
        )

    def allow_ingress_from(self, peer: ec2.IPeer) -> None:
        """Let one source reach the listener. Everything else is refused."""
        self.load_balancer.connections.allow_from(peer, ec2.Port.tcp(LISTENER_PORT), "CloudFront")
