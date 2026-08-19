from aws_cdk import Stack
from aws_cdk import aws_certificatemanager as acm
from aws_cdk import aws_route53 as route53
from constructs import Construct

from argus_infra.config import ArgusEnv
from argus_infra.constructs.api import Api
from argus_infra.constructs.database import Database
from argus_infra.constructs.network import Network
from argus_infra.constructs.observability import Observability
from argus_infra.constructs.registry import Registry
from argus_infra.constructs.web import Web
from argus_infra.parameters import Parameters


class ArgusStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, *, env_config: ArgusEnv, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        self.env_config = env_config
        self.parameters = Parameters(env_config.name)
        self.image_tag = self.parameters.deploy_time_value(self, "image-tag")

        domain_name = self.parameters.synth_time_value(self, "domain-name")
        api_hostname = self.parameters.synth_time_value(self, "api-hostname")

        self.zone = route53.HostedZone.from_lookup(self, "Zone", domain_name=domain_name)
        self.certificate = acm.Certificate(
            self,
            "Certificate",
            domain_name=domain_name,
            subject_alternative_names=[domain_name, api_hostname],
            validation=acm.CertificateValidation.from_dns(self.zone),
        )

        self.network = Network(self, "Network")
        self.registry = Registry(self, "Registry")
        self.database = Database(
            self,
            "Database",
            vpc=self.network.vpc,
            instance_class=env_config.db_instance_class,
        )
        self.api = Api(
            self,
            "Api",
            vpc=self.network.vpc,
            repository=self.registry.repository,
            image_tag=self.image_tag,
            env_config=env_config,
            certificate=self.certificate,
            api_hostname=api_hostname,
            zone=self.zone,
            database=self.database.instance,
            parameters=self.parameters,
        )
        self.web = Web(
            self,
            "Web",
            domain_name=domain_name,
            certificate=self.certificate,
            zone=self.zone,
        )
        self.observability = Observability(
            self,
            "Observability",
            target_group=self.api.service.target_group,
            log_group=self.api.log_group,
            database=self.database.instance,
        )
