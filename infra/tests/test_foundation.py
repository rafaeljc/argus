import json

import pytest
from aws_cdk import App, Stack
from aws_cdk.assertions import Match, Template

from argus.config import EnvironmentConfig
from argus.stacks.foundation import FoundationStack

GITHUB_ISSUER = "token.actions.githubusercontent.com"


@pytest.fixture(scope="module")
def stack(config: EnvironmentConfig) -> Stack:
    return FoundationStack(App(), "argus-prod-foundation", config=config)


@pytest.fixture(scope="module")
def template(stack: Stack) -> Template:
    return Template.from_stack(stack)


def logical_id_of(template: Template, resource_type: str, name_property: str, name: str) -> str:
    found = template.find_resources(resource_type, {"Properties": {name_property: name}})
    assert len(found) == 1, f"expected exactly one {resource_type} named {name}, got {list(found)}"
    return next(iter(found))


# --- the image repository ------------------------------------------------------------------


def test_the_repository_is_named_without_an_environment(template: Template) -> None:
    template.has_resource_properties("AWS::ECR::Repository", {"RepositoryName": "argus-backend"})


def test_pushed_images_are_scanned_for_vulnerabilities(template: Template) -> None:
    template.has_resource_properties(
        "AWS::ECR::Repository", {"ImageScanningConfiguration": {"ScanOnPush": True}}
    )


def test_deleting_the_stack_does_not_strand_every_deployed_tag(template: Template) -> None:
    template.has_resource(
        "AWS::ECR::Repository",
        {"DeletionPolicy": "Retain", "UpdateReplacePolicy": "Retain"},
    )


def test_untagged_images_expire_and_tagged_images_are_capped(template: Template) -> None:
    repositories = template.find_resources("AWS::ECR::Repository")
    policy = next(iter(repositories.values()))["Properties"]["LifecyclePolicy"]
    rules = json.loads(policy["LifecyclePolicyText"])["rules"]

    by_status = {rule["selection"]["tagStatus"]: rule["selection"] for rule in rules}
    assert by_status["untagged"]["countNumber"] == 7
    assert by_status["untagged"]["countUnit"] == "days"
    assert by_status["tagged"]["countNumber"] == 20


# --- deployment identity -------------------------------------------------------------------


def test_github_actions_can_federate_into_this_account(template: Template) -> None:
    template.has_resource_properties(
        "Custom::AWSCDKOpenIdConnectProvider",
        {"Url": f"https://{GITHUB_ISSUER}", "ClientIDList": ["sts.amazonaws.com"]},
    )


@pytest.mark.parametrize("component", ["backend", "frontend"])
def test_each_deployment_role_is_scoped_to_the_environment(
    template: Template, component: str
) -> None:
    template.has_resource_properties("AWS::IAM::Role", {"RoleName": f"argus-prod-cd-{component}"})


@pytest.mark.parametrize("component", ["backend", "frontend"])
def test_only_the_default_branch_of_this_repository_may_assume_a_role(
    template: Template, component: str
) -> None:
    template.has_resource_properties(
        "AWS::IAM::Role",
        {
            "RoleName": f"argus-prod-cd-{component}",
            "AssumeRolePolicyDocument": Match.object_like(
                {
                    "Statement": [
                        Match.object_like(
                            {
                                "Action": "sts:AssumeRoleWithWebIdentity",
                                "Condition": {
                                    "StringEquals": {
                                        f"{GITHUB_ISSUER}:aud": "sts.amazonaws.com",
                                        f"{GITHUB_ISSUER}:sub": (
                                            "repo:rafaeljc/argus:ref:refs/heads/main"
                                        ),
                                    }
                                },
                            }
                        )
                    ]
                }
            ),
        },
    )


def test_the_backend_role_may_push_images_and_record_the_deployed_tag(
    template: Template,
) -> None:
    backend_role = logical_id_of(template, "AWS::IAM::Role", "RoleName", "argus-prod-cd-backend")
    granted = _actions_granted_to(template, backend_role)

    assert "ecr:PutImage" in granted
    assert "ecr:GetAuthorizationToken" in granted
    assert "ssm:PutParameter" in granted


def test_the_frontend_role_gains_only_what_this_stack_owns(template: Template) -> None:
    frontend_role = logical_id_of(template, "AWS::IAM::Role", "RoleName", "argus-prod-cd-frontend")

    # The bucket and distribution are granted by the stack that owns them.
    assert _actions_granted_to(template, frontend_role) == {"ssm:GetParameter"}


@pytest.mark.parametrize("component", ["backend", "frontend"])
def test_each_role_can_read_the_parameters_its_workflow_discovers_through(
    template: Template, component: str
) -> None:
    """Publishing a discovery value is useless if the reader cannot read it."""
    role = logical_id_of(template, "AWS::IAM::Role", "RoleName", f"argus-prod-cd-{component}")
    readable = _resources_readable_by(template, role)

    assert any(f"parameter/argus/prod/out/{component}/*" in resource for resource in readable), (
        readable
    )


def _resources_readable_by(template: Template, role_logical_id: str) -> list[str]:
    """ARNs a role may GetParameter on, rendered as text.

    Rendered rather than compared structurally because CDK emits them as
    ``Fn::Join`` fragments around the partition, not as plain strings.
    """
    resources: list[str] = []
    for policy in template.find_resources("AWS::IAM::Policy").values():
        properties = policy["Properties"]
        if not any(role.get("Ref") == role_logical_id for role in properties["Roles"]):
            continue
        for statement in properties["PolicyDocument"]["Statement"]:
            action = statement["Action"]
            actions = [action] if isinstance(action, str) else action
            if "ssm:GetParameter" in actions:
                resources.append(json.dumps(statement["Resource"]))
    return resources


def _actions_granted_to(template: Template, role_logical_id: str) -> set[str]:
    """Every action any inline policy in this stack grants to one role."""
    actions: set[str] = set()
    for policy in template.find_resources("AWS::IAM::Policy").values():
        properties = policy["Properties"]
        if not any(role.get("Ref") == role_logical_id for role in properties["Roles"]):
            continue
        for statement in properties["PolicyDocument"]["Statement"]:
            action = statement["Action"]
            actions.update([action] if isinstance(action, str) else action)
    return actions


# --- alarm delivery ------------------------------------------------------------------------


def test_critical_alarms_reach_a_human_by_email(template: Template) -> None:
    template.has_resource_properties("AWS::SNS::Topic", {"TopicName": "argus-prod-alarms"})
    template.has_resource_properties(
        "AWS::SNS::Subscription",
        {"Protocol": "email", "Endpoint": "ops@argusapp.click"},
    )


# --- discovery -----------------------------------------------------------------------------


def test_the_backend_workflow_can_find_the_repository_without_knowing_stack_names(
    template: Template,
) -> None:
    repository = logical_id_of(template, "AWS::ECR::Repository", "RepositoryName", "argus-backend")
    published = _published_outputs(template)

    # Published as references, so the values track the repository rather than
    # restating a name that could drift away from it.
    assert published["/argus/prod/out/backend/repository-name"] == {"Ref": repository}
    assert "/argus/prod/out/backend/repository-uri" in published


def test_only_values_a_workflow_actually_reads_are_published(template: Template) -> None:
    # Later stacks reach the alarm topic through its deterministic ARN rather
    # than a parameter, so nothing here exists without a reader.
    assert set(_published_outputs(template)) == {
        "/argus/prod/out/backend/repository-name",
        "/argus/prod/out/backend/repository-uri",
    }


def test_discovery_parameters_are_deleted_with_the_stack(template: Template) -> None:
    for parameter in template.find_resources("AWS::SSM::Parameter").values():
        assert parameter.get("DeletionPolicy", "Delete") == "Delete"


def _published_outputs(template: Template) -> dict[str, object]:
    return {
        parameter["Properties"]["Name"]: parameter["Properties"]["Value"]
        for parameter in template.find_resources("AWS::SSM::Parameter").values()
    }
