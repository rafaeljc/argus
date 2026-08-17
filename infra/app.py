#!/usr/bin/env python3
from aws_cdk import App, Environment

from argus_infra.argus_stack import ArgusStack
from argus_infra.config import ENVIRONMENTS

app = App()

for env_name, env_config in ENVIRONMENTS.items():
    ArgusStack(
        app,
        f"Argus-{env_name.capitalize()}",
        env_config=env_config,
        env=Environment(account=app.account, region="us-east-1"),
    )

app.synth()
