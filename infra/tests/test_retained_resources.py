"""The guard against an update leaving resources behind.

Retained resources survive both stack deletion and *replacement*, so they are
the only ones that can be orphaned. This pins exactly which resources those are,
by logical id -- the thing that changes when someone renames a construct, which
is the usual accidental cause of a replacement.

A failure here is not a test to fix. It means either a resource just became
orphanable, or a retained one is about to be replaced. Update
``retained_resources.json`` only after deciding that is what you want.
"""

import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

import pytest
from aws_cdk import App, Stack
from aws_cdk.assertions import Template

from argus.config import EnvironmentConfig
from argus.environment import build_environment

GOLDEN_FILE = Path(__file__).parent / "retained_resources.json"

# Where each retained resource type carries its explicit physical name.
NAME_PROPERTY = {
    "AWS::ECR::Repository": "RepositoryName",
    "AWS::S3::Bucket": "BucketName",
    "AWS::RDS::DBInstance": "DBInstanceIdentifier",
    "AWS::SecretsManager::Secret": "Name",
}

Resource = Mapping[str, Any]


@pytest.fixture(scope="module")
def templates(config: EnvironmentConfig) -> dict[str, Template]:
    app = App()
    build_environment(app, config)
    return {
        stack.stack_name: Template.from_stack(stack)
        for stack in app.node.children
        if isinstance(stack, Stack)
    }


@pytest.fixture(scope="module")
def retained(templates: dict[str, Template]) -> list[dict[str, Any]]:
    found = [
        {
            "stack": stack_name,
            "logical_id": logical_id,
            "type": resource["Type"],
            "physical_name": resource["Properties"].get(NAME_PROPERTY.get(resource["Type"], "")),
        }
        for stack_name, template in templates.items()
        for logical_id, resource in template.to_json()["Resources"].items()
        if resource.get("DeletionPolicy") == "Retain"
    ]
    return sorted(found, key=lambda entry: (entry["stack"], entry["logical_id"]))


def test_exactly_the_expected_resources_outlive_their_stack(
    retained: list[dict[str, Any]],
) -> None:
    assert retained == json.loads(GOLDEN_FILE.read_text())


def test_a_retained_resource_is_never_replaced_silently(
    templates: dict[str, Template], retained: list[dict[str, Any]]
) -> None:
    """UpdateReplacePolicy must match, or a replacement orphans the original."""
    for entry in retained:
        resource = templates[entry["stack"]].to_json()["Resources"][entry["logical_id"]]
        assert resource.get("UpdateReplacePolicy") == "Retain", entry["logical_id"]


def test_every_retained_resource_has_a_name_to_collide_on(
    retained: list[dict[str, Any]],
) -> None:
    """An explicit name turns a forced replacement into a failed deploy.

    Without one, CloudFormation creates a replacement under a fresh generated
    name and leaves the original behind, still holding the data and still
    costing money.
    """
    for entry in retained:
        assert entry["type"] in NAME_PROPERTY, f"{entry['type']} has no known name property"
        assert entry["physical_name"], f"{entry['logical_id']} has no explicit physical name"


def test_everything_else_is_destroyed_with_its_stack(
    templates: dict[str, Template],
) -> None:
    for stack_name, template in templates.items():
        for logical_id, resource in template.to_json()["Resources"].items():
            policy = resource.get("DeletionPolicy", "Delete")
            assert policy in {"Delete", "Retain"}, f"{stack_name}/{logical_id}: {policy}"
