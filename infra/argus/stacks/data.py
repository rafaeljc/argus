"""The Postgres instance holding everything that cannot be rebuilt.

This is the only stack whose resources are retained. Everything else in this
project can be destroyed and recreated from the repository; the database cannot,
so the instance and the credentials that open it both outlive their stack, and
both carry explicit physical names. A change that forces replacement then fails
on a name collision rather than quietly leaving the original behind.

The instance opens no ingress of its own. The compute stack, which owns the task
that connects, grants itself the port.
"""

from aws_cdk import Duration
from aws_cdk import aws_cloudwatch as cloudwatch
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_rds as rds
from aws_cdk import aws_secretsmanager as secretsmanager
from constructs import Construct

from argus.config import DATABASE, EnvironmentConfig
from argus.constructs.alarms import CriticalAlarms
from argus.retention import Durability
from argus.stacks.base import ArgusStack

ENGINE_VERSION = rds.PostgresEngineVersion.VER_18

CPU_ALARM_PERCENT = 80
FREE_STORAGE_ALARM_BYTES = 2 * 1024**3
# db.t4g.micro allows a little over 100; alarm before the application starts
# seeing connection refusals.
CONNECTIONS_ALARM_COUNT = 80


class DataStack(ArgusStack):
    """Postgres, its generated credentials, and the alarms that precede an outage."""

    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        config: EnvironmentConfig,
        vpc: ec2.IVpc,
    ) -> None:
        super().__init__(scope, construct_id, config=config)

        # Created here rather than left to the instance to generate, so that the
        # removal policy below lands on the secret itself. Reaching for
        # ``database.secret`` instead would apply it to the target attachment,
        # leaving the credentials on their default of Delete -- which deletes
        # the only way into a database that is deliberately retained.
        self.credentials = rds.DatabaseSecret(
            self,
            "DatabaseSecret",
            username=DATABASE.username,
            secret_name=f"argus/{config.name}/db",
        )
        self.credentials.apply_removal_policy(Durability.RETAINED.removal_policy)

        self.database = rds.DatabaseInstance(
            self,
            "Database",
            instance_identifier=self.naming.resource("db"),
            engine=rds.DatabaseInstanceEngine.postgres(version=ENGINE_VERSION),
            instance_type=ec2.InstanceType(DATABASE.instance_class),
            vpc=vpc,
            # Created here rather than generated, because the instance's RETAIN
            # would otherwise propagate to it. A subnet group is a list of
            # subnet ids -- nothing to lose, and a retained one with a generated
            # name lingers after teardown and can block deleting the VPC.
            #
            # The cost of that choice is teardown ordering: this is deleted with
            # the stack while the instance it holds is not, so destroying while
            # the instance still exists fails with "at least one database
            # instance is still using it". Delete the instance first -- see the
            # teardown section of README.md.
            subnet_group=rds.SubnetGroup(
                self,
                "DatabaseSubnetGroup",
                description="Isolated subnets for the Argus database",
                vpc=vpc,
                vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_ISOLATED),
                removal_policy=Durability.DISPOSABLE.removal_policy,
            ),
            credentials=rds.Credentials.from_secret(self.credentials),
            # Without this the generated secret has no dbname field, and the
            # backend assembles its JDBC url from those fields.
            database_name=DATABASE.database_name,
            allocated_storage=DATABASE.allocated_storage_gib,
            storage_type=rds.StorageType.GP3,
            storage_encrypted=True,
            multi_az=False,
            publicly_accessible=False,
            backup_retention=Duration.days(DATABASE.backup_retention_days),
            delete_automated_backups=False,
            deletion_protection=True,
            auto_minor_version_upgrade=True,
            allow_major_version_upgrade=False,
            removal_policy=Durability.RETAINED.removal_policy,
        )

        self._alarm_on_conditions_that_precede_an_outage()

    @property
    def connection_secret(self) -> secretsmanager.ISecret:
        """Credentials plus host, port and dbname, injected field by field into the task.

        This is the attached form of :attr:`credentials`: attaching to the
        instance is what fills in the endpoint fields, which the backend needs
        because it assembles its own JDBC url.
        """
        attached = self.database.secret
        if attached is None:  # pragma: no cover - always present when credentials are given
            raise ValueError("the database was created without credentials")
        return attached

    def _alarm_on_conditions_that_precede_an_outage(self) -> None:
        alarms = CriticalAlarms(self, self.naming)
        alarms.add(
            "db-cpu-saturated",
            metric=self.database.metric_cpu_utilization(),
            threshold=CPU_ALARM_PERCENT,
            description="The database has been CPU bound long enough to slow every request.",
        )
        alarms.add(
            "db-storage-exhausted",
            metric=self.database.metric_free_storage_space(),
            threshold=FREE_STORAGE_ALARM_BYTES,
            comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
            description="The database is running out of disk and will stop accepting writes.",
        )
        alarms.add(
            "db-connections-exhausted",
            metric=self.database.metric_database_connections(),
            threshold=CONNECTIONS_ALARM_COUNT,
            description="The database is close to refusing new connections.",
        )
