from aws_cdk import CfnOutput, Duration, RemovalPolicy
from aws_cdk import aws_certificatemanager as acm
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_ecr as ecr
from aws_cdk import aws_ecs as ecs
from aws_cdk import aws_ecs_patterns as ecs_patterns
from aws_cdk import aws_elasticloadbalancingv2 as elbv2
from aws_cdk import aws_logs as logs
from aws_cdk import aws_rds as rds
from aws_cdk import aws_route53 as route53
from aws_cdk import aws_secretsmanager as secretsmanager
from constructs import Construct

from argus_infra.config import ArgusEnv
from argus_infra.parameters import Parameters

VENDOR_KEYS_SECRET_NAME = "argus/vendor-keys"


class Api(Construct):
    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        vpc: ec2.Vpc,
        repository: ecr.IRepository,
        image_tag: str,
        env_config: ArgusEnv,
        certificate: acm.ICertificate,
        api_hostname: str,
        zone: route53.IHostedZone,
        database: rds.DatabaseInstance,
        parameters: Parameters,
    ) -> None:
        super().__init__(scope, construct_id)

        self.log_group = logs.LogGroup(
            self,
            "ApiLogs",
            retention=env_config.log_retention,
            removal_policy=RemovalPolicy.DESTROY,
        )

        cluster = ecs.Cluster(self, "Cluster", vpc=vpc)

        vendor_keys = secretsmanager.Secret.from_secret_name_v2(
            self, "VendorKeys", VENDOR_KEYS_SECRET_NAME
        )
        db_secret = database.secret
        assert db_secret is not None, "Database must be created with from_generated_secret"

        self.service = ecs_patterns.ApplicationLoadBalancedFargateService(
            self,
            "Service",
            cluster=cluster,
            desired_count=1,
            cpu=env_config.task_cpu,
            memory_limit_mib=env_config.task_memory_mib,
            assign_public_ip=False,
            task_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS),
            public_load_balancer=True,
            protocol=elbv2.ApplicationProtocol.HTTPS,
            certificate=certificate,
            redirect_http=True,
            domain_name=api_hostname,
            domain_zone=zone,
            circuit_breaker=ecs.DeploymentCircuitBreaker(rollback=True),
            min_healthy_percent=0,
            max_healthy_percent=100,
            task_image_options=ecs_patterns.ApplicationLoadBalancedTaskImageOptions(
                image=ecs.ContainerImage.from_ecr_repository(repository, image_tag),
                container_port=8080,
                environment={
                    "SPRING_PROFILES_ACTIVE": env_config.spring_profile,
                },
                secrets={
                    "ARGUS_DB_HOST": ecs.Secret.from_secrets_manager(db_secret, "host"),
                    "ARGUS_DB_PORT": ecs.Secret.from_secrets_manager(db_secret, "port"),
                    "ARGUS_DB_NAME": ecs.Secret.from_secrets_manager(db_secret, "dbname"),
                    "ARGUS_DB_USERNAME": ecs.Secret.from_secrets_manager(db_secret, "username"),
                    "ARGUS_DB_PASSWORD": ecs.Secret.from_secrets_manager(db_secret, "password"),
                    "ARGUS_EMAIL_RESEND_API_KEY": ecs.Secret.from_secrets_manager(
                        vendor_keys, "EMAIL_API_KEY"
                    ),
                    "ARGUS_MARKETDATA_MASSIVE_API_KEY": ecs.Secret.from_secrets_manager(
                        vendor_keys, "MARKETDATA_API_KEY"
                    ),
                    "ARGUS_APP_BASE_URL": parameters.ssm_secret(self, "AppBaseUrlParam", "app-base-url"),
                    "ARGUS_WEB_CORS_ALLOWED_ORIGIN": parameters.ssm_secret(
                        self, "CorsAllowedOriginParam", "cors-allowed-origin"
                    ),
                    "ARGUS_WEB_COOKIE_DOMAIN": parameters.ssm_secret(
                        self, "CookieDomainParam", "cookie-domain"
                    ),
                    "ARGUS_EMAIL_ADDRESS": parameters.ssm_secret(
                        self, "EmailAddressParam", "email-from-address"
                    ),
                    "ARGUS_EMAIL_RESEND_API_URL": parameters.ssm_secret(
                        self, "EmailResendApiUrlParam", "email-api-base-url"
                    ),
                    "ARGUS_MARKETDATA_MASSIVE_API_URL": parameters.ssm_secret(
                        self, "MarketdataMassiveApiUrlParam", "marketdata-base-url"
                    ),
                },
                log_driver=ecs.LogDrivers.aws_logs(stream_prefix="argus", log_group=self.log_group),
            ),
        )

        self.service.target_group.configure_health_check(
            path="/actuator/health/readiness",
            port="8081",
            healthy_http_codes="200",
        )

        default_container = self.service.task_definition.default_container
        assert default_container is not None
        default_container.add_port_mappings(ecs.PortMapping(container_port=8081))

        self.service.service.connections.allow_from(
            self.service.load_balancer,
            ec2.Port.tcp(8081),
            "ALB health check on the management port",
        )
        self.service.service.connections.allow_to(
            database,
            ec2.Port.tcp(5432),
            "App reads/writes Postgres",
        )

        CfnOutput(self, "ClusterName", value=cluster.cluster_name)
        CfnOutput(self, "ServiceName", value=self.service.service.service_name)
        CfnOutput(self, "TaskDefinitionFamily", value=self.service.task_definition.family)
        CfnOutput(self, "ContainerName", value=default_container.container_name)
