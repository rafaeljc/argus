import dataclasses

import pytest
from aws_cdk import aws_logs as logs

from argus_infra.config import ENVIRONMENTS, ArgusEnv


def test_environments_contains_prod():
    assert "prod" in ENVIRONMENTS


def test_prod_env_name_matches_ssm_path_segment():
    assert ENVIRONMENTS["prod"].name == "prod"


def test_prod_env_uses_prod_spring_profile():
    assert ENVIRONMENTS["prod"].spring_profile == "prod"


def test_prod_env_log_retention_is_one_month():
    assert ENVIRONMENTS["prod"].log_retention == logs.RetentionDays.ONE_MONTH


def test_arguenv_is_frozen():
    env = ENVIRONMENTS["prod"]
    with pytest.raises(dataclasses.FrozenInstanceError):
        env.name = "mutated"
