from collections.abc import Mapping
from typing import Any

import pytest
from aws_cdk import App
from aws_cdk.assertions import Match, Template

from argus.config import EnvironmentConfig
from argus.stacks.network import NetworkStack

VPC_CIDR = "10.0.0.0/16"

Resource = Mapping[str, Any]


@pytest.fixture(scope="module")
def stack(config: EnvironmentConfig) -> NetworkStack:
    return NetworkStack(App(), "argus-prod-network", config=config)


@pytest.fixture(scope="module")
def template(stack: NetworkStack) -> Template:
    return Template.from_stack(stack)


# --- shape of the network ------------------------------------------------------------------


def test_the_network_spans_two_availability_zones(template: Template) -> None:
    # Three subnet groups across two zones.
    template.resource_count_is("AWS::EC2::Subnet", 6)


def test_names_resolve_inside_the_vpc(template: Template) -> None:
    # Without both, the task cannot resolve the database endpoint.
    template.has_resource_properties(
        "AWS::EC2::VPC",
        {"CidrBlock": VPC_CIDR, "EnableDnsSupport": True, "EnableDnsHostnames": True},
    )


def test_the_database_tier_has_no_route_to_the_internet(template: Template) -> None:
    isolated = _subnets_named(template, "database")

    assert len(isolated) == 2
    for subnet in isolated.values():
        assert subnet["Properties"].get("MapPublicIpOnLaunch") is not True


# --- egress is a NAT instance, never a NAT gateway -----------------------------------------


def test_egress_never_costs_a_nat_gateway(template: Template) -> None:
    template.resource_count_is("AWS::EC2::NatGateway", 0)


def test_egress_runs_on_the_smallest_graviton_instance(template: Template) -> None:
    template.has_resource_properties("AWS::EC2::Instance", {"InstanceType": "t4g.nano"})


def test_the_nat_instance_boots_an_image_matching_its_architecture(template: Template) -> None:
    """A t4g is Graviton; an x86 image would leave it unbootable."""
    image_parameters = [
        parameter["Default"]
        for parameter in template.to_json().get("Parameters", {}).values()
        if str(parameter.get("Default", "")).startswith("/aws/service/ami")
    ]

    assert image_parameters, "expected the AMI to resolve through an SSM parameter"
    assert all("arm64" in default for default in image_parameters)


def test_only_the_vpc_may_route_through_the_nat_instance(template: Template) -> None:
    ingress = _nat_ingress_rules(template)

    assert ingress, "private subnets cannot reach the internet without an ingress rule"
    assert all(rule.get("CidrIp") == VPC_CIDR for rule in ingress)


def test_the_nat_instance_is_not_open_to_the_internet(template: Template) -> None:
    assert all(rule.get("CidrIp") != "0.0.0.0/0" for rule in _nat_ingress_rules(template))


def test_no_interface_endpoints_are_paid_for(template: Template) -> None:
    # At this traffic volume an interface endpoint costs more than the NAT
    # instance it would replace.
    template.resource_count_is("AWS::EC2::VPCEndpoint", 0)


# --- failure of the single NAT instance is noticed -----------------------------------------


def test_losing_the_only_route_to_the_internet_raises_an_alarm(template: Template) -> None:
    template.has_resource_properties(
        "AWS::CloudWatch::Alarm",
        Match.object_like(
            {
                "MetricName": "StatusCheckFailed",
                "Namespace": "AWS/EC2",
                "AlarmActions": Match.any_value(),
            }
        ),
    )


def test_alarms_are_delivered_to_the_environment_topic(template: Template) -> None:
    alarms = template.find_resources("AWS::CloudWatch::Alarm")
    actions = [
        action for alarm in alarms.values() for action in alarm["Properties"]["AlarmActions"]
    ]

    assert actions
    for action in actions:
        assert "argus-prod-alarms" in str(action)


# --- nothing here is worth keeping ---------------------------------------------------------


def test_the_whole_network_can_be_rebuilt_from_this_repository(template: Template) -> None:
    for logical_id, resource in template.to_json()["Resources"].items():
        assert resource.get("DeletionPolicy", "Delete") == "Delete", logical_id


def _subnets_named(template: Template, group: str) -> dict[str, Resource]:
    return {
        logical_id: subnet
        for logical_id, subnet in template.find_resources("AWS::EC2::Subnet").items()
        if any(
            tag["Key"] == "aws-cdk:subnet-name" and tag["Value"] == group
            for tag in subnet["Properties"].get("Tags", [])
        )
    }


def _nat_ingress_rules(template: Template) -> list[Resource]:
    rules: list[Resource] = []
    for group in template.find_resources("AWS::EC2::SecurityGroup").values():
        rules.extend(group["Properties"].get("SecurityGroupIngress", []))
    rules.extend(
        rule["Properties"]
        for rule in template.find_resources("AWS::EC2::SecurityGroupIngress").values()
    )
    return rules
