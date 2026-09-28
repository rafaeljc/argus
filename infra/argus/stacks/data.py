"""The stateful tier: Postgres, and the Redis session store beside it.

The database's resources are retained -- everything else in this project can be
destroyed and recreated from the repository, but the database cannot, so the
instance and the credentials that open it both outlive their stack, and both
carry explicit physical names. A change that forces replacement then fails on a
name collision rather than quietly leaving the original behind.

The cache is different: sessions are disposable by design (losing them logs
everyone out), so every cache resource uses the ordinary DISPOSABLE policy.

Neither the database nor the cache opens ingress of its own. Each rule is
created in the scope that already depends on the other: this stack's secrets
are read by the compute stack, so a rule granting the task access has to live
in compute instead, or the two stacks would depend on each other and fail
synthesis with a cyclic reference.
"""

from aws_cdk import Duration, SecretValue
from aws_cdk import aws_cloudwatch as cloudwatch
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_elasticache as elasticache
from aws_cdk import aws_rds as rds
from aws_cdk import aws_secretsmanager as secretsmanager
from constructs import Construct

from argus.config import CACHE, DATABASE, EnvironmentConfig
from argus.constructs.alarms import CriticalAlarms, cache_metric
from argus.retention import Durability
from argus.stacks.base import ArgusStack

DATABASE_ENGINE_VERSION = rds.PostgresEngineVersion.VER_18

DATABASE_CPU_ALARM_PERCENT = 80
DATABASE_FREE_STORAGE_ALARM_BYTES = 2 * 1024**3
# db.t4g.micro allows a little over 100; alarm before the application starts
# seeing connection refusals.
DATABASE_CONNECTIONS_ALARM_COUNT = 80

CACHE_CPU_ALARM_PERCENT = 80
CACHE_MEMORY_ALARM_PERCENT = 80
CACHE_CONNECTIONS_ALARM_COUNT = 80

# ElastiCache's Redis OSS ceiling: 7.2 and above is Valkey only. Kept as its own
# engine version rather than a shared constant with Postgres -- the two engines
# version independently and nothing is gained by coupling them.
CACHE_ENGINE = "redis"
CACHE_ENGINE_VERSION = "7.1"
CACHE_PARAMETER_GROUP_FAMILY = "redis7"

# Every user group must hold a user *named* "default". The *id* "default" is the
# one ElastiCache creates per account and region, which can be neither created
# nor modified -- and which, left in the group on its managed "on ~* +@all",
# would make the IAM-authenticated user below decorative: anything reaching the
# port could authenticate as it with no token at all. Only the name is fixed, so
# the group gets its own disabled user under it and the managed one never joins.
# https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Clusters.RBAC.html
DEFAULT_USER_NAME = "default"
DEFAULT_USER_ACCESS_STRING = "off -@all"

# ~argus:* covers the argus:session namespace Spring Session writes under.
# &__key*@0__:* covers the keyspace/keyevent channels the indexed session
# repository subscribes to on db 0. +@all rather than a narrower category:
# Spring Boot's Redis health indicator issues INFO, which sits in @dangerous,
# and -@dangerous would fail the readiness probe the ALB depends on.
# ElastiCache blocks the genuinely destructive admin commands on its own.
#
# Redis matches a PSUBSCRIBE pattern against the ACL literally rather than as a
# glob, so the session-created channel has to be spelled out exactly as Spring
# Session builds it from spring.session.data.redis.namespace and the database
# index -- a broader &argus:* is still refused with NOPERM.
SESSION_CREATED_CHANNEL_PATTERN = "argus:session:event:0:created:*"
BACKEND_USER_ACCESS_STRING = f"on ~argus:* &__key*@0__:* &{SESSION_CREATED_CHANNEL_PATTERN} +@all"


class DataStack(ArgusStack):
    """Postgres and Redis: their generated credentials, and the alarms that precede an outage."""

    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        config: EnvironmentConfig,
        vpc: ec2.IVpc,
    ) -> None:
        super().__init__(scope, construct_id, config=config)
        self._alarms = CriticalAlarms(self, self.naming)

        self.database_credentials = self._database_credentials(config)
        self.database = self._database(vpc)
        self._alarm_on_database_conditions_that_precede_an_outage()

        self.cache_security_group = self._cache_security_group(vpc)
        self.cache_users = self._cache_users(config)
        self.cache_user_group = self._cache_user_group(config)
        self.cache = self._cache(config, vpc)
        self._alarm_on_cache_conditions_that_precede_an_outage()
        self.cache_connection_details = self._cache_connection_details(config)

    def _database_credentials(self, config: EnvironmentConfig) -> rds.DatabaseSecret:
        # Created here rather than left to the instance to generate, so that the
        # removal policy below lands on the secret itself. Reaching for
        # ``database.secret`` instead would apply it to the target attachment,
        # leaving the credentials on their default of Delete -- which deletes
        # the only way into a database that is deliberately retained.
        credentials = rds.DatabaseSecret(
            self,
            "DatabaseSecret",
            username=DATABASE.username,
            secret_name=f"argus/{config.name}/db",
        )
        credentials.apply_removal_policy(Durability.RETAINED.removal_policy)
        return credentials

    def _database(self, vpc: ec2.IVpc) -> rds.DatabaseInstance:
        return rds.DatabaseInstance(
            self,
            "Database",
            instance_identifier=self.naming.resource("db"),
            engine=rds.DatabaseInstanceEngine.postgres(version=DATABASE_ENGINE_VERSION),
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
            credentials=rds.Credentials.from_secret(self.database_credentials),
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

    @property
    def database_connection_secret(self) -> secretsmanager.ISecret:
        """Credentials plus host, port and dbname, injected field by field into the task.

        This is the attached form of :attr:`database_credentials`: attaching to
        the instance is what fills in the endpoint fields, which the backend
        needs because it assembles its own JDBC url.
        """
        attached = self.database.secret
        if attached is None:  # pragma: no cover - always present when credentials are given
            raise ValueError("the database was created without credentials")
        return attached

    def _alarm_on_database_conditions_that_precede_an_outage(self) -> None:
        self._alarms.add(
            "db-cpu-saturated",
            metric=self.database.metric_cpu_utilization(),
            threshold=DATABASE_CPU_ALARM_PERCENT,
            description="The database has been CPU bound long enough to slow every request.",
        )
        self._alarms.add(
            "db-storage-exhausted",
            metric=self.database.metric_free_storage_space(),
            threshold=DATABASE_FREE_STORAGE_ALARM_BYTES,
            comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
            description="The database is running out of disk and will stop accepting writes.",
        )
        self._alarms.add(
            "db-connections-exhausted",
            metric=self.database.metric_database_connections(),
            threshold=DATABASE_CONNECTIONS_ALARM_COUNT,
            description="The database is close to refusing new connections.",
        )

    def _alarm_on_cache_conditions_that_precede_an_outage(self) -> None:
        # The dimension below is a plain string, not a token derived from
        # self.cache, so CloudFormation has no implicit ordering between these
        # alarms and the replication group -- add_dependency states it explicitly.
        cache_cluster_id = f"{self.cache.replication_group_id}-001"
        cpu_alarm = self._alarms.add(
            "cache-cpu-saturated",
            metric=cache_metric(cache_cluster_id, "EngineCPUUtilization"),
            threshold=CACHE_CPU_ALARM_PERCENT,
            description="The cache has been CPU bound long enough to slow every session read.",
        )
        memory_alarm = self._alarms.add(
            "cache-memory-exhausted",
            metric=cache_metric(cache_cluster_id, "DatabaseMemoryUsagePercentage"),
            threshold=CACHE_MEMORY_ALARM_PERCENT,
            description="The cache is running out of memory and will start evicting live sessions.",
        )
        connections_alarm = self._alarms.add(
            "cache-connections-exhausted",
            metric=cache_metric(cache_cluster_id, "CurrConnections"),
            threshold=CACHE_CONNECTIONS_ALARM_COUNT,
            description="The cache is close to refusing new connections.",
        )
        cpu_alarm.node.add_dependency(self.cache)
        memory_alarm.node.add_dependency(self.cache)
        connections_alarm.node.add_dependency(self.cache)

    def _cache_security_group(self, vpc: ec2.IVpc) -> ec2.SecurityGroup:
        # Declared explicitly, unlike every other security group in this
        # project: CfnReplicationGroup takes raw group ids rather than
        # exposing the ``.connections`` an L2 construct would.
        return ec2.SecurityGroup(
            self,
            "CacheSecurityGroup",
            vpc=vpc,
            description="The Argus cache (Redis session store)",
            allow_all_outbound=False,
        )

    def _cache_users(self, config: EnvironmentConfig) -> list[elasticache.CfnUser]:
        default_user = elasticache.CfnUser(
            self,
            "CacheDefaultUser",
            engine=CACHE_ENGINE,
            # Any id but "default"; only the name has to be.
            user_id=self.naming.resource("cache-default"),
            user_name=DEFAULT_USER_NAME,
            access_string=DEFAULT_USER_ACCESS_STRING,
            no_password_required=True,
        )
        backend_user_id = self.naming.resource("backend")
        backend_user = elasticache.CfnUser(
            self,
            "CacheBackendUser",
            engine=CACHE_ENGINE,
            # Identical id and name are required for IAM-enabled users.
            user_id=backend_user_id,
            user_name=backend_user_id,
            access_string=BACKEND_USER_ACCESS_STRING,
            authentication_mode={"Type": "iam"},
        )
        return [default_user, backend_user]

    def _cache_user_group(self, config: EnvironmentConfig) -> elasticache.CfnUserGroup:
        return elasticache.CfnUserGroup(
            self,
            "CacheUserGroup",
            engine=CACHE_ENGINE,
            user_group_id=self.naming.resource("cache-users"),
            # Ref, not the literal id: it is the same string, but it is also the
            # dependency that orders the users ahead of the group.
            user_ids=[user.ref for user in self.cache_users],
        )

    def _cache(self, config: EnvironmentConfig, vpc: ec2.IVpc) -> elasticache.CfnReplicationGroup:
        subnet_group = elasticache.CfnSubnetGroup(
            self,
            "CacheSubnetGroup",
            description="Isolated subnets for the Argus Redis session store",
            subnet_ids=vpc.select_subnets(subnet_type=ec2.SubnetType.PRIVATE_ISOLATED).subnet_ids,
        )
        parameter_group = elasticache.CfnParameterGroup(
            self,
            "CacheParameterGroup",
            cache_parameter_group_family=CACHE_PARAMETER_GROUP_FAMILY,
            description="Enables the keyspace notifications Spring Session relies on",
            properties={
                # ElastiCache blocks the CONFIG command Spring Session's default
                # configure-action would otherwise issue to set this itself;
                # application-prod.yaml sets configure-action: none to match.
                "notify-keyspace-events": "Egx",
            },
        )
        return elasticache.CfnReplicationGroup(
            self,
            "Cache",
            replication_group_id=self.naming.resource("cache"),
            replication_group_description="The Argus Redis session store",
            engine=CACHE_ENGINE,
            engine_version=CACHE_ENGINE_VERSION,
            cache_node_type=CACHE.node_type,
            port=CACHE.port,
            num_cache_clusters=1,
            automatic_failover_enabled=False,
            multi_az_enabled=False,
            cache_subnet_group_name=subnet_group.ref,
            cache_parameter_group_name=parameter_group.ref,
            security_group_ids=[self.cache_security_group.security_group_id],
            user_group_ids=[self.cache_user_group.ref],
            transit_encryption_enabled=True,
            transit_encryption_mode="required",
            at_rest_encryption_enabled=True,
            snapshot_retention_limit=0,
            auto_minor_version_upgrade=True,
        )

    def _cache_connection_details(self, config: EnvironmentConfig) -> secretsmanager.Secret:
        """Host, port, IAM user id and cache name, injected field by field into the task.

        ElastiCache has no equivalent of attaching credentials to an RDS
        instance to fill in its endpoint fields, so this secret is assembled
        by hand from the replication group's own attributes. It carries no
        credential -- authentication is IAM -- which is why it is disposable
        rather than retained: everything in it can be rebuilt from this stack.
        """
        backend_user_id = self.naming.resource("backend")
        secret = secretsmanager.Secret(
            self,
            "CacheConnectionDetails",
            secret_name=f"argus/{config.name}/cache",
            secret_object_value={
                "host": SecretValue.unsafe_plain_text(self.cache.attr_primary_end_point_address),
                "port": SecretValue.unsafe_plain_text(self.cache.attr_primary_end_point_port),
                "username": SecretValue.unsafe_plain_text(backend_user_id),
                "cache-name": SecretValue.unsafe_plain_text(self.cache.replication_group_id or ""),
            },
        )
        secret.apply_removal_policy(Durability.DISPOSABLE.removal_policy)
        return secret
