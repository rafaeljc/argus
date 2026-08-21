from collections.abc import Mapping
from typing import Any

import pytest
from aws_cdk import App
from aws_cdk.assertions import Template

from argus.config import EnvironmentConfig
from argus.stacks.data import DataStack
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
