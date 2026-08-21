import pytest

from argus.config import EnvironmentConfig


@pytest.fixture(scope="session")
def config() -> EnvironmentConfig:
    return EnvironmentConfig(
        name="prod",
        account="123456789012",
        region="us-east-1",
        domain_name="argusapp.click",
        hosted_zone_id="Z0123456789ABCDEFGHIJ",
        image_tag="1f2e3d4c5b6a798877665544332211ffeeddccbb",
        alert_email="ops@argusapp.click",
        cloudfront_prefix_list_id="pl-3b927c52",
    )
