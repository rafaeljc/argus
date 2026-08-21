import pytest
from aws_cdk import App
from aws_cdk.assertions import Template

from argus.config import EnvironmentConfig
from argus.environment import build_environment

EXPECTED_STACKS = {
    "argus-prod-foundation",
    "argus-prod-network",
    "argus-prod-data",
    "argus-prod-compute",
    "argus-prod-edge",
}


@pytest.fixture(scope="module")
def app(config: EnvironmentConfig, tmp_path_factory: pytest.TempPathFactory) -> App:
    application = App(outdir=str(tmp_path_factory.mktemp("cdk")))
    build_environment(application, config)
    # Cross-stack references are only resolved into dependencies during synth,
    # so the deploy order does not exist before this call.
    application.synth()
    return application


def test_every_stack_is_named_for_its_environment_and_concern(app: App) -> None:
    assert {stack.stack_name for stack in app.node.children if hasattr(stack, "stack_name")} == (
        EXPECTED_STACKS
    )


def test_the_first_pass_stack_depends_on_nothing(app: App) -> None:
    """Pass one has to deploy into an account with no VPC and no image."""
    foundation = _stack_named(app, "argus-prod-foundation")

    assert foundation.dependencies == []


def test_the_compute_stack_cannot_deploy_before_what_it_reads(app: App) -> None:
    compute = _stack_named(app, "argus-prod-compute")

    assert {dependency.stack_name for dependency in compute.dependencies} >= {
        "argus-prod-network",
        "argus-prod-data",
    }


def test_the_environment_is_pinned_to_one_account_and_region(app: App) -> None:
    for stack in _stacks(app):
        assert stack.account == "123456789012"
        assert stack.region == "us-east-1"


@pytest.mark.parametrize("stack_name", sorted(EXPECTED_STACKS))
def test_every_resource_is_traceable_back_to_this_project(app: App, stack_name: str) -> None:
    """Tags are what make the orphan audit possible at all."""
    template = Template.from_stack(_stack_named(app, stack_name)).to_json()
    tagged = [
        resource
        for resource in template["Resources"].values()
        if "Tags" in resource.get("Properties", {})
    ]

    assert tagged, f"{stack_name} has no taggable resources to check"
    for resource in tagged:
        tags = _tags_of(resource)
        assert tags.get("argus:environment") == "prod"
        assert tags.get("argus:managed-by") == "cdk"


def _tags_of(resource: dict) -> dict[str, str]:  # type: ignore[type-arg]
    """Most resources render Tags as a list of pairs; a few render a map."""
    tags = resource["Properties"]["Tags"]
    if isinstance(tags, dict):
        return tags
    return {tag["Key"]: tag["Value"] for tag in tags}


# Every AWS action each deployment workflow performs, in the order its steps
# run. A role's permissions are granted across three stacks, so this is the only
# place the whole contract is visible -- and the only place a missing grant shows
# up before a workflow fails on it.
BACKEND_WORKFLOW_ACTIONS = {
    "ssm:GetParameter",  # read infrastructure outputs
    "ecr:GetAuthorizationToken",  # log in to ECR
    "ecr:InitiateLayerUpload",  # build and push
    "ecr:UploadLayerPart",
    "ecr:CompleteLayerUpload",
    "ecr:BatchCheckLayerAvailability",
    "ecr:PutImage",
    "ssm:PutParameter",  # point SSM at this SHA
    "ecs:DescribeTaskDefinition",  # render task definition
    "ecs:RegisterTaskDefinition",  # deploy
    "ecs:TagResource",  # deploy: every resource is tagged, so registering tags
    "iam:PassRole",  # deploy: hands ECS the roles the definition names
    "ecs:UpdateService",
    "ecs:DescribeServices",  # wait for service stability
    "ecs:ListTaskDefinitions",  # deregister superseded revisions
    "ecs:DeregisterTaskDefinition",
}

FRONTEND_WORKFLOW_ACTIONS = {
    "ssm:GetParameter",  # read infrastructure outputs
    "s3:PutObject",  # upload assets and index.html
    "s3:DeleteObject",  # sync --delete
    "s3:ListBucket",  # sync compares against what is there
    "cloudfront:CreateInvalidation",
}


@pytest.mark.parametrize(
    ("component", "required"),
    [("backend", BACKEND_WORKFLOW_ACTIONS), ("frontend", FRONTEND_WORKFLOW_ACTIONS)],
)
def test_each_deploy_role_can_perform_every_step_of_its_workflow(
    app: App, component: str, required: set[str]
) -> None:
    role_name = f"argus-prod-cd-{component}"
    granted = set()
    for stack in _stacks(app):
        granted |= _actions_granted_to(Template.from_stack(stack), role_name)

    missing = {action for action in required if not _covered_by(action, granted)}
    assert not missing, f"{role_name} cannot: {sorted(missing)}"


def _covered_by(action: str, granted: set[str]) -> bool:
    """CDK's grants use wildcard action names, e.g. s3:DeleteObject*."""
    return any(
        allowed == action or (allowed.endswith("*") and action.startswith(allowed[:-1]))
        for allowed in granted
    )


def _actions_granted_to(template: Template, role_name: str) -> set[str]:
    """Actions granted to a role, whether it is created here or imported by name."""
    actions: set[str] = set()
    roles_by_id = {
        logical_id: resource["Properties"].get("RoleName")
        for logical_id, resource in template.find_resources("AWS::IAM::Role").items()
    }
    for policy in template.find_resources("AWS::IAM::Policy").values():
        properties = policy["Properties"]
        attached = {
            roles_by_id.get(role["Ref"]) if isinstance(role, dict) else role
            for role in properties["Roles"]
        }
        if role_name not in attached:
            continue
        for statement in properties["PolicyDocument"]["Statement"]:
            action = statement["Action"]
            actions.update([action] if isinstance(action, str) else action)
    return actions


def _stacks(app: App) -> list:  # type: ignore[type-arg]
    from aws_cdk import Stack

    return [child for child in app.node.children if isinstance(child, Stack)]


def _stack_named(app: App, name: str):  # type: ignore[no-untyped-def]
    matching = [stack for stack in _stacks(app) if stack.stack_name == name]
    assert len(matching) == 1, f"no stack named {name}"
    return matching[0]
