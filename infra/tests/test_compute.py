from collections.abc import Mapping
from typing import Any

import pytest
from aws_cdk import App
from aws_cdk.assertions import Match, Template

from argus.config import EnvironmentConfig
from argus.stacks.compute import ComputeStack
from argus.stacks.data import DataStack
from argus.stacks.network import NetworkStack

Resource = Mapping[str, Any]

CLOUDFRONT_PREFIX_LIST = "pl-3b927c52"

EXPECTED_SECRETS = {
    "ARGUS_APP_BASE_URL",
    "ARGUS_WEB_CORS_ALLOWED_ORIGIN",
    "ARGUS_WEB_COOKIE_DOMAIN",
    "ARGUS_EMAIL_ADDRESS",
    "ARGUS_EMAIL_RESEND_API_URL",
    "ARGUS_MARKETDATA_MASSIVE_API_URL",
    "ARGUS_EMAIL_RESEND_API_KEY",
    "ARGUS_MARKETDATA_MASSIVE_API_KEY",
    "ARGUS_DB_HOST",
    "ARGUS_DB_PORT",
    "ARGUS_DB_NAME",
    "ARGUS_DB_USERNAME",
    "ARGUS_DB_PASSWORD",
}


@pytest.fixture(scope="module")
def stack(config: EnvironmentConfig) -> ComputeStack:
    app = App()
    network = NetworkStack(app, "argus-prod-network", config=config)
    data = DataStack(app, "argus-prod-data", config=config, vpc=network.vpc)
    return ComputeStack(
        app,
        "argus-prod-compute",
        config=config,
        vpc=network.vpc,
        database=data.database,
        connection_secret=data.connection_secret,
    )


@pytest.fixture(scope="module")
def template(stack: ComputeStack) -> Template:
    return Template.from_stack(stack)


# --- how the service is deployed -----------------------------------------------------------


def test_the_template_does_not_dictate_how_many_tasks_run(template: Template) -> None:
    """A DesiredCount here would revert any scaling done outside CloudFormation."""
    service = _only_resource(template, "AWS::ECS::Service")

    assert "DesiredCount" not in service["Properties"]


def test_a_failing_deployment_rolls_itself_back(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ECS::Service",
        Match.object_like(
            {
                "DeploymentConfiguration": Match.object_like(
                    {"DeploymentCircuitBreaker": {"Enable": True, "Rollback": True}}
                )
            }
        ),
    )


def test_the_old_task_stops_before_the_new_one_starts(template: Template) -> None:
    # One task at a time: never pay for two, at the cost of a brief gap.
    template.has_resource_properties(
        "AWS::ECS::Service",
        Match.object_like(
            {
                "DeploymentConfiguration": Match.object_like(
                    {"MinimumHealthyPercent": 0, "MaximumPercent": 100}
                )
            }
        ),
    )


def test_tasks_have_no_public_address(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ECS::Service",
        Match.object_like(
            {
                "NetworkConfiguration": Match.object_like(
                    {"AwsvpcConfiguration": Match.object_like({"AssignPublicIp": "DISABLED"})}
                )
            }
        ),
    )


# --- the task ------------------------------------------------------------------------------


def test_the_task_is_sized_as_documented(template: Template) -> None:
    template.has_resource_properties("AWS::ECS::TaskDefinition", {"Cpu": "1024", "Memory": "2048"})


def test_the_container_serves_traffic_and_health_on_separate_ports(template: Template) -> None:
    ports = {mapping["ContainerPort"] for mapping in _container(template)["PortMappings"]}

    assert ports == {8080, 8081}


def test_every_configured_value_is_injected_as_a_secret(template: Template) -> None:
    container = _container(template)

    assert {secret["Name"] for secret in container["Secrets"]} == EXPECTED_SECRETS


def test_the_only_plain_environment_variable_selects_the_profile(template: Template) -> None:
    environment = _container(template)["Environment"]

    assert environment == [{"Name": "SPRING_PROFILES_ACTIVE", "Value": "prod"}]


def test_the_container_runs_the_image_recorded_in_the_parameter(template: Template) -> None:
    assert "1f2e3d4c5b6a798877665544332211ffeeddccbb" in str(_container(template)["Image"])


def test_a_month_of_logs_is_kept(template: Template) -> None:
    template.has_resource_properties("AWS::Logs::LogGroup", {"RetentionInDays": 30})


# --- reaching the service ------------------------------------------------------------------


def test_the_load_balancer_is_not_exposed_to_the_internet(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ElasticLoadBalancingV2::LoadBalancer",
        Match.object_like({"Scheme": "internal"}),
    )


def test_only_cloudfront_may_reach_the_load_balancer(template: Template) -> None:
    sources = {
        rule.get("SourcePrefixListId") or rule.get("CidrIp")
        for rule in _ingress_rules(template)
        if rule.get("ToPort") == 80
    }

    assert sources == {CLOUDFRONT_PREFIX_LIST}


def test_health_is_checked_on_the_management_port(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ElasticLoadBalancingV2::TargetGroup",
        Match.object_like(
            {
                "Port": 8080,
                "HealthCheckPort": "8081",
                "HealthCheckPath": "/actuator/health/ready",
            }
        ),
    )


def test_the_load_balancer_may_reach_both_container_ports(template: Template) -> None:
    # add_targets opens only the traffic port; the health check port needs its own rule.
    opened = {rule.get("FromPort") for rule in _ingress_rules(template)}

    assert {8080, 8081} <= opened


def test_the_task_may_reach_the_database(template: Template) -> None:
    """The rule belongs to this stack: adding it in the data stack would be a cycle."""
    assert any(rule.get("ToPort") == 5432 for rule in _ingress_rules(template))


# --- deployment permissions ----------------------------------------------------------------


def test_the_backend_role_may_roll_the_service_it_deploys(template: Template) -> None:
    granted = _granted_actions(template)

    assert {
        "ecs:DescribeServices",
        "ecs:UpdateService",
        "ecs:RegisterTaskDefinition",
        "ecs:DescribeTaskDefinition",
        "iam:PassRole",
    } <= granted


def test_the_backend_role_may_prune_the_revisions_it_accumulates(template: Template) -> None:
    """Every deploy registers a revision and AWS never reaps them."""
    granted = _granted_actions(template)

    assert {"ecs:ListTaskDefinitions", "ecs:DeregisterTaskDefinition"} <= granted


# --- discovery -----------------------------------------------------------------------------


def test_the_backend_workflow_can_find_everything_it_needs(template: Template) -> None:
    published = {
        parameter["Properties"]["Name"]
        for parameter in template.find_resources("AWS::SSM::Parameter").values()
    }

    assert published == {
        "/argus/prod/out/backend/cluster-name",
        "/argus/prod/out/backend/service-name",
        "/argus/prod/out/backend/task-definition-family",
        "/argus/prod/out/backend/container-name",
    }


# --- nothing here is worth keeping ---------------------------------------------------------


def test_the_whole_compute_layer_can_be_rebuilt(template: Template) -> None:
    for logical_id, resource in template.to_json()["Resources"].items():
        assert resource.get("DeletionPolicy", "Delete") == "Delete", logical_id


def _only_resource(template: Template, resource_type: str) -> Resource:
    resources = template.find_resources(resource_type)
    assert len(resources) == 1, f"expected one {resource_type}, got {list(resources)}"
    return next(iter(resources.values()))


def _container(template: Template) -> Resource:
    definitions = _only_resource(template, "AWS::ECS::TaskDefinition")["Properties"][
        "ContainerDefinitions"
    ]
    assert len(definitions) == 1
    container: Resource = definitions[0]
    return container


def _ingress_rules(template: Template) -> list[Resource]:
    rules: list[Resource] = [
        rule["Properties"]
        for rule in template.find_resources("AWS::EC2::SecurityGroupIngress").values()
    ]
    for group in template.find_resources("AWS::EC2::SecurityGroup").values():
        rules.extend(group["Properties"].get("SecurityGroupIngress", []))
    return rules


def _granted_actions(template: Template) -> set[str]:
    return {
        action
        for policy in template.find_resources("AWS::IAM::Policy").values()
        for statement in policy["Properties"]["PolicyDocument"]["Statement"]
        for action in _as_list(statement["Action"])
    }


def _as_list(action: Any) -> list[str]:
    return [action] if isinstance(action, str) else list(action)
