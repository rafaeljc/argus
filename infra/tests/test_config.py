import dataclasses

import pytest

from argus.config import BACKEND, DATABASE, EnvironmentConfig

CONFIG = EnvironmentConfig(
    name="prod",
    account="123456789012",
    region="us-east-1",
    domain_name="argusapp.click",
    hosted_zone_id="Z0123456789ABCDEFGHIJ",
    image_tag="1f2e3d4c5b6a798877665544332211ffeeddccbb",
    alert_email="ops@argusapp.click",
    cloudfront_prefix_list_id="pl-3b927c52",
)


def test_configuration_cannot_be_mutated_after_construction() -> None:
    with pytest.raises(dataclasses.FrozenInstanceError):
        CONFIG.image_tag = "other"  # type: ignore[misc]


def test_naming_is_derived_from_the_environment_name() -> None:
    assert CONFIG.naming.resource("compute") == "argus-prod-compute"


def test_deploys_only_from_the_default_branch_of_the_project_repository() -> None:
    assert CONFIG.github_repository == "rafaeljc/argus"
    assert CONFIG.github_branch == "main"


def test_backend_sizing_matches_the_documented_task_shape() -> None:
    assert (BACKEND.cpu, BACKEND.memory_mib) == (1024, 2048)
    assert (BACKEND.traffic_port, BACKEND.management_port) == (8080, 8081)


def test_database_retains_two_weeks_of_backups() -> None:
    assert DATABASE.backup_retention_days == 14
