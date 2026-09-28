from collections.abc import Mapping
from typing import Any

import pytest
from aws_cdk import App
from aws_cdk.assertions import Template

from argus.config import EnvironmentConfig
from argus.stacks.data import SESSION_CREATED_CHANNEL_PATTERN, DataStack
from argus.stacks.network import NetworkStack

Resource = Mapping[str, Any]


@pytest.fixture(scope="module")
def stacks(config: EnvironmentConfig) -> tuple[Template, Template]:
    app = App()
    network = NetworkStack(app, "argus-prod-network", config=config)
    data = DataStack(app, "argus-prod-data", config=config, vpc=network.vpc)
    return Template.from_stack(network), Template.from_stack(data)


@pytest.fixture(scope="module")
def template(stacks: tuple[Template, Template]) -> Template:
    return stacks[1]


# --- shape of the instance -----------------------------------------------------------------


def test_the_engine_matches_the_one_the_backend_is_tested_against(template: Template) -> None:
    template.has_resource_properties(
        "AWS::RDS::DBInstance", {"Engine": "postgres", "EngineVersion": "18"}
    )


def test_minor_versions_are_patched_automatically_but_majors_are_not(template: Template) -> None:
    # A major upgrade can break the application; a minor one carries fixes.
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {"AutoMinorVersionUpgrade": True, "AllowMajorVersionUpgrade": False},
    )


def test_the_instance_is_sized_for_the_budget(template: Template) -> None:
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {
            "DBInstanceClass": "db.t4g.micro",
            "AllocatedStorage": "20",
            "StorageType": "gp3",
            "MultiAZ": False,
        },
    )


def test_the_application_database_is_created_with_the_instance(template: Template) -> None:
    template.has_resource_properties("AWS::RDS::DBInstance", {"DBName": "argus"})


# --- reachability --------------------------------------------------------------------------


def test_the_database_is_never_reachable_from_the_internet(template: Template) -> None:
    template.has_resource_properties("AWS::RDS::DBInstance", {"PubliclyAccessible": False})


def test_the_database_sits_in_the_isolated_tier(stacks: tuple[Template, Template]) -> None:
    network, data = stacks
    subnet_ids = _only_resource(data, "AWS::RDS::DBSubnetGroup")["Properties"]["SubnetIds"]

    placed_in = {_resolve_import(network, subnet_id) for subnet_id in subnet_ids}

    assert placed_in == set(_subnets_named(network, "database"))


def test_nothing_may_reach_the_database_until_a_stack_grants_it(template: Template) -> None:
    # The compute stack opens 5432 for the task; this stack opens nothing.
    ingress = [
        rule["Properties"]
        for rule in template.find_resources("AWS::EC2::SecurityGroupIngress").values()
    ]
    for group in template.find_resources("AWS::EC2::SecurityGroup").values():
        ingress.extend(group["Properties"].get("SecurityGroupIngress", []))

    assert ingress == []


# --- durability ----------------------------------------------------------------------------


def test_the_data_survives_deletion_of_its_stack(template: Template) -> None:
    template.has_resource(
        "AWS::RDS::DBInstance", {"DeletionPolicy": "Retain", "UpdateReplacePolicy": "Retain"}
    )


def test_the_credentials_survive_with_it(template: Template) -> None:
    # Losing the generated secret locks everyone out of a database that is
    # still there.
    template.has_resource(
        "AWS::SecretsManager::Secret",
        {"DeletionPolicy": "Retain", "UpdateReplacePolicy": "Retain"},
    )


def test_the_instance_cannot_be_deleted_by_accident(template: Template) -> None:
    template.has_resource_properties("AWS::RDS::DBInstance", {"DeletionProtection": True})


def test_two_weeks_of_backups_are_kept_and_outlive_the_instance(template: Template) -> None:
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {"BackupRetentionPeriod": 14, "DeleteAutomatedBackups": False},
    )


def test_the_instance_is_named_so_a_replacement_collides_instead_of_orphaning(
    template: Template,
) -> None:
    template.has_resource_properties(
        "AWS::RDS::DBInstance", {"DBInstanceIdentifier": "argus-prod-db"}
    )


def test_the_data_at_rest_is_encrypted(template: Template) -> None:
    template.has_resource_properties("AWS::RDS::DBInstance", {"StorageEncrypted": True})


# --- alarms --------------------------------------------------------------------------------


def test_the_conditions_that_take_the_database_down_are_alarmed(template: Template) -> None:
    alarmed = {
        alarm["Properties"]["MetricName"]
        for alarm in template.find_resources("AWS::CloudWatch::Alarm").values()
    }

    assert alarmed == {"CPUUtilization", "FreeStorageSpace", "DatabaseConnections"}


def test_running_out_of_disk_alarms_on_the_way_down(template: Template) -> None:
    storage = next(
        alarm["Properties"]
        for alarm in template.find_resources("AWS::CloudWatch::Alarm").values()
        if alarm["Properties"]["MetricName"] == "FreeStorageSpace"
    )

    assert storage["ComparisonOperator"] == "LessThanOrEqualToThreshold"


# --- the cache -------------------------------------------------------------------------------


def test_the_cache_engine_is_redis_not_valkey(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ElastiCache::ReplicationGroup", {"Engine": "redis", "EngineVersion": "7.1"}
    )


def test_the_cache_has_no_cluster_mode_and_no_replica(template: Template) -> None:
    # The cheapest topology that still serves the workload: one node, no
    # failover target, no second AZ to fail over into.
    template.has_resource_properties(
        "AWS::ElastiCache::ReplicationGroup",
        {
            "NumCacheClusters": 1,
            "AutomaticFailoverEnabled": False,
            "MultiAZEnabled": False,
        },
    )


def test_the_cache_is_sized_for_the_session_store_workload(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ElastiCache::ReplicationGroup", {"CacheNodeType": "cache.t4g.micro"}
    )


def test_traffic_to_the_cache_is_encrypted_in_transit(template: Template) -> None:
    # Mandatory, and also a prerequisite of the IAM authentication below.
    template.has_resource_properties(
        "AWS::ElastiCache::ReplicationGroup",
        {"TransitEncryptionEnabled": True, "TransitEncryptionMode": "required"},
    )


def test_the_cache_data_at_rest_is_encrypted(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ElastiCache::ReplicationGroup", {"AtRestEncryptionEnabled": True}
    )


def test_the_cache_is_never_reachable_from_outside_its_security_group(
    template: Template,
) -> None:
    # No inline ingress on the cache's own group -- the compute stack opens
    # 6379 for the task, mirroring how it opens 5432 for the database.
    groups = template.find_resources("AWS::EC2::SecurityGroup")
    cache_group = next(
        group
        for group in groups.values()
        if "cache" in group["Properties"].get("GroupDescription", "").lower()
    )

    assert cache_group["Properties"].get("SecurityGroupIngress", []) == []


def test_the_cache_sits_in_the_isolated_tier(stacks: tuple[Template, Template]) -> None:
    network, data = stacks
    subnet_ids = _only_resource(data, "AWS::ElastiCache::SubnetGroup")["Properties"]["SubnetIds"]

    placed_in = {_resolve_import(network, subnet_id) for subnet_id in subnet_ids}

    assert placed_in == set(_subnets_named(network, "database"))


def test_the_default_user_is_locked_out(template: Template) -> None:
    # ElastiCache requires a user named "default" in every user group. Left at
    # the AWS-managed default (nopass, +@all) it would make the IAM user
    # decorative -- anything reaching the port could authenticate as it.
    users = _cache_user_properties(template)
    default = next(user for user in users if user["UserName"] == "default")

    assert default["AccessString"] == "off -@all"
    assert default["NoPasswordRequired"] is True
    # Only the *name* has to be "default". The id belongs to the user
    # ElastiCache creates per account and region, which can be neither created
    # nor modified, so claiming it is a create that can never succeed.
    assert default["UserId"] != "default"


def test_the_backend_user_authenticates_with_iam(template: Template) -> None:
    users = _cache_user_properties(template)
    backend = next(user for user in users if user["UserName"] != "default")

    assert backend["AuthenticationMode"] == {"Type": "iam"}
    # Identical id and name are required for IAM-enabled users.
    assert backend["UserId"] == backend["UserName"]
    assert "PASSWORD" not in backend and "Passwords" not in backend


def test_the_backend_user_may_subscribe_to_the_session_created_channel(
    template: Template,
) -> None:
    # Spring Session PSUBSCRIBEs to this while the context is still refreshing;
    # without the literal pattern the backend exits on NOPERM before serving.
    users = _cache_user_properties(template)
    backend = next(user for user in users if user["UserName"] != "default")

    assert f"&{SESSION_CREATED_CHANNEL_PATTERN}" in backend["AccessString"]


def test_the_user_group_holds_both_users(template: Template) -> None:
    group = _only_resource(template, "AWS::ElastiCache::UserGroup")

    assert len(group["Properties"]["UserIds"]) == 2


def test_the_parameter_group_enables_keyspace_notifications_for_spring_session(
    template: Template,
) -> None:
    # application-prod.yaml sets configure-action: none because ElastiCache
    # blocks the CONFIG command Spring Session would otherwise issue itself.
    template.has_resource_properties(
        "AWS::ElastiCache::ParameterGroup",
        {"Properties": {"notify-keyspace-events": "Egx"}},
    )


def test_nothing_may_reach_the_cache_until_the_compute_stack_grants_it(
    template: Template,
) -> None:
    ingress = [
        rule["Properties"]
        for rule in template.find_resources("AWS::EC2::SecurityGroupIngress").values()
    ]
    for group in template.find_resources("AWS::EC2::SecurityGroup").values():
        ingress.extend(group["Properties"].get("SecurityGroupIngress", []))

    assert ingress == []


def test_the_cache_is_disposable_unlike_the_database(template: Template) -> None:
    # Sessions are disposable -- losing them logs everyone out -- so unlike the
    # database this carries no explicit removal policy, which leaves
    # CloudFormation's own default of Delete in place.
    resource = _only_resource(template, "AWS::ElastiCache::ReplicationGroup")
    assert resource.get("DeletionPolicy", "Delete") == "Delete"


def test_the_cache_connection_secret_is_disposable(template: Template) -> None:
    # Unlike argus/prod/db: it holds no credential, only connection details
    # that can be rebuilt from this stack.
    secret = next(
        secret["Properties"]
        for secret in template.find_resources("AWS::SecretsManager::Secret").values()
        if secret["Properties"].get("Name", "").endswith("/cache")
    )

    assert "GenerateSecretString" not in secret


def _cache_user_properties(template: Template) -> list[Resource]:
    users = template.find_resources("AWS::ElastiCache::User")
    return [user["Properties"] for user in users.values()]


def _only_resource(template: Template, resource_type: str) -> Resource:
    resources = template.find_resources(resource_type)
    assert len(resources) == 1, f"expected one {resource_type}, got {list(resources)}"
    return next(iter(resources.values()))


def _subnets_named(template: Template, group: str) -> dict[str, Resource]:
    return {
        logical_id: subnet
        for logical_id, subnet in template.find_resources("AWS::EC2::Subnet").items()
        if any(
            tag["Key"] == "aws-cdk:subnet-name" and tag["Value"] == group
            for tag in subnet["Properties"].get("Tags", [])
        )
    }


def _resolve_import(exporting: Template, value: Mapping[str, Any]) -> str:
    """Follow an Fn::ImportValue back to the logical id it names in the other stack."""
    export_name = value["Fn::ImportValue"]
    outputs = exporting.to_json()["Outputs"]
    matching = [
        output["Value"]
        for output in outputs.values()
        if output.get("Export", {}).get("Name") == export_name
    ]
    assert len(matching) == 1, f"no unique export named {export_name}"
    return str(matching[0]["Ref"])
