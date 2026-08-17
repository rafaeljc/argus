from aws_cdk import Duration
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_rds as rds
from constructs import Construct


class Database(Construct):
    def __init__(self, scope: Construct, construct_id: str, *, vpc: ec2.Vpc, instance_class: str) -> None:
        super().__init__(scope, construct_id)

        instance_type, instance_size = instance_class.removeprefix("db.").split(".")

        self.instance = rds.DatabaseInstance(
            self,
            "Postgres",
            # Highest 18.x this aws-cdk-lib release exposes. Re-confirm against the real
            # account before first deploy — `aws rds describe-db-engine-versions` needs
            # credentials this environment doesn't have. Fall back to 17 if 18 is absent;
            # `ddl-auto: validate` and the single V1__init.sql are version-agnostic.
            engine=rds.DatabaseInstanceEngine.postgres(version=rds.PostgresEngineVersion.VER_18_3),
            instance_type=ec2.InstanceType.of(
                ec2.InstanceClass[instance_type.upper()], ec2.InstanceSize[instance_size.upper()]
            ),
            vpc=vpc,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_ISOLATED),
            allocated_storage=20,
            storage_type=rds.StorageType.GP3,
            storage_encrypted=True,
            multi_az=False,
            backup_retention=Duration.days(7),
            publicly_accessible=False,
            database_name="argus",
            credentials=rds.Credentials.from_generated_secret("argus"),
        )
