from aws_cdk import aws_ec2 as ec2
from constructs import Construct


class Network(Construct):
    def __init__(self, scope: Construct, construct_id: str) -> None:
        super().__init__(scope, construct_id)

        self.vpc = ec2.Vpc(
            self,
            "Vpc",
            max_azs=2,
            nat_gateways=1,
            subnet_configuration=[
                ec2.SubnetConfiguration(name="Public", subnet_type=ec2.SubnetType.PUBLIC),
                ec2.SubnetConfiguration(
                    name="PrivateWithEgress", subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS
                ),
                ec2.SubnetConfiguration(
                    name="PrivateIsolated", subnet_type=ec2.SubnetType.PRIVATE_ISOLATED
                ),
            ],
        )
