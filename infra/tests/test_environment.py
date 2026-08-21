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


def _stacks(app: App) -> list:  # type: ignore[type-arg]
    from aws_cdk import Stack

    return [child for child in app.node.children if isinstance(child, Stack)]


def _stack_named(app: App, name: str):  # type: ignore[no-untyped-def]
    matching = [stack for stack in _stacks(app) if stack.stack_name == name]
    assert len(matching) == 1, f"no stack named {name}"
    return matching[0]
