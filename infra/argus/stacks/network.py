"""The VPC everything else runs inside.

Three tiers: a public tier holding only the internet gateway and the NAT
instance, a private tier with egress for the load balancer and tasks, and an
isolated tier with no route off the VPC at all for the database.

Egress is a NAT *instance*, not a NAT gateway. A gateway would cost more per
month than the rest of this environment combined, which the budget in
``docs/NFR.md`` does not allow. The trade is a single point of failure: if it
dies, image pulls, Secrets Manager and both vendor APIs go with it. That is
accepted under the single-AZ availability target, and alarmed here so it is at
least noticed rather than discovered.
"""

from aws_cdk import aws_ec2 as ec2
from constructs import Construct

from argus.config import EnvironmentConfig
from argus.constructs.alarms import CriticalAlarms, instance_status_check
from argus.stacks.base import ArgusStack

VPC_CIDR = "10.0.0.0/16"
AVAILABILITY_ZONES = 2
NAT_INSTANCE_TYPE = "t4g.nano"

PUBLIC_TIER = "public"
APPLICATION_TIER = "application"
DATABASE_TIER = "database"


class NetworkStack(ArgusStack):
    """VPC, subnets, and the single instance every outbound request leaves through."""

    def __init__(self, scope: Construct, construct_id: str, *, config: EnvironmentConfig) -> None:
        super().__init__(scope, construct_id, config=config)

        nat_provider = self._nat_provider()
        self.vpc = ec2.Vpc(
            self,
            "Vpc",
            vpc_name=self.naming.resource("vpc"),
            ip_addresses=ec2.IpAddresses.cidr(VPC_CIDR),
            max_azs=AVAILABILITY_ZONES,
            # The database resolves its own endpoint by name, and so does
            # anything reaching Secrets Manager.
            enable_dns_support=True,
            enable_dns_hostnames=True,
            nat_gateway_provider=nat_provider,
            nat_gateways=1,
            subnet_configuration=[
                ec2.SubnetConfiguration(
                    name=PUBLIC_TIER, subnet_type=ec2.SubnetType.PUBLIC, cidr_mask=24
                ),
                ec2.SubnetConfiguration(
                    name=APPLICATION_TIER,
                    subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS,
                    cidr_mask=24,
                ),
                ec2.SubnetConfiguration(
                    name=DATABASE_TIER,
                    subnet_type=ec2.SubnetType.PRIVATE_ISOLATED,
                    cidr_mask=24,
                ),
            ],
        )

        # OUTBOUND_ONLY leaves the NAT instance with no ingress at all, so the
        # rule that lets private subnets route through it has to be added by
        # hand. That is the point: the default would have opened it to the
        # whole internet.
        nat_provider.connections.allow_from(ec2.Peer.ipv4(VPC_CIDR), ec2.Port.all_traffic())

        self._alarm_on_lost_egress(nat_provider)

    def _nat_provider(self) -> ec2.NatInstanceProviderV2:
        return ec2.NatProvider.instance_v2(
            instance_type=ec2.InstanceType(NAT_INSTANCE_TYPE),
            # A t4g is Graviton, so the image has to be ARM. Resolved through
            # an SSM parameter at deploy time rather than cached into context,
            # so a redeploy always picks up the current patched AMI.
            machine_image=ec2.MachineImage.latest_amazon_linux2023(
                cpu_type=ec2.AmazonLinuxCpuType.ARM_64
            ),
            default_allowed_traffic=ec2.NatTrafficDirection.OUTBOUND_ONLY,
        )

    def _alarm_on_lost_egress(self, nat_provider: ec2.NatInstanceProviderV2) -> None:
        alarms = CriticalAlarms(self, self.naming)
        for index, instance in enumerate(nat_provider.gateway_instances):
            alarms.add(
                f"nat-instance-{index}-unreachable",
                metric=instance_status_check(instance.instance_id),
                threshold=1,
                description=(
                    "The NAT instance is failing its status checks. Everything in the "
                    "private subnets has lost its route to the internet."
                ),
            )
