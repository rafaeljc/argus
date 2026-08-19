import pytest
from aws_cdk import App, Environment
from aws_cdk.assertions import Template

from argus_infra.argus_stack import ArgusStack
from argus_infra.config import ENVIRONMENTS

TEST_AWS_ENV = Environment(account="123456789012", region="us-east-1")


@pytest.fixture
def stack() -> ArgusStack:
    app = App()
    return ArgusStack(app, "Test", env_config=ENVIRONMENTS["prod"], env=TEST_AWS_ENV)


@pytest.fixture
def template(stack: ArgusStack) -> Template:
    return Template.from_stack(stack)
