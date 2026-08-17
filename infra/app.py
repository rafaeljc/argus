#!/usr/bin/env python3
import os

from aws_cdk import App, Environment

from argus_infra.argus_stack import ArgusStack
from argus_infra.config import ENVIRONMENTS

app = App()

# CDK_DEFAULT_ACCOUNT/_REGION are set by the CDK CLI from the active AWS profile.
# The domain-name and api-hostname synth-time lookups need a concrete account, so
# a real `cdk synth`/`cdk deploy` needs working AWS credentials — offline testing
# goes through pytest instead, which supplies a fixed test account explicitly.
account = os.environ["CDK_DEFAULT_ACCOUNT"]
region = os.environ.get("CDK_DEFAULT_REGION", "us-east-1")

for env_name, env_config in ENVIRONMENTS.items():
    ArgusStack(
        app,
        f"Argus-{env_name.capitalize()}",
        env_config=env_config,
        env=Environment(account=account, region=region),
    )

app.synth()
