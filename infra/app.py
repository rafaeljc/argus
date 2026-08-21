#!/usr/bin/env python3
"""CDK entry point.

Reads every input from the account, builds the environment, and synthesizes. All
the AWS access lives behind :mod:`argus.lookups`, so a missing input fails here,
in seconds, rather than partway through a CloudFormation rollback.
"""

import os
import sys

from aws_cdk import App

from argus.config import EnvironmentConfig
from argus.environment import build_environment
from argus.lookups import AwsLookups, MissingInputsError, resolve_inputs
from argus.naming import Naming

ENVIRONMENT_NAME = "prod"


def main() -> int:
    account = os.environ.get("CDK_DEFAULT_ACCOUNT")
    region = os.environ.get("CDK_DEFAULT_REGION")
    if not account or not region:
        print(
            "No AWS account resolved. Run through the cdk CLI with credentials "
            "available, or set CDK_DEFAULT_ACCOUNT and CDK_DEFAULT_REGION.",
            file=sys.stderr,
        )
        return 1

    naming = Naming(environment=ENVIRONMENT_NAME)
    try:
        inputs = resolve_inputs(AwsLookups(), naming)
    except MissingInputsError as missing:
        print(missing, file=sys.stderr)
        return 1

    app = App()
    build_environment(
        app,
        EnvironmentConfig(
            name=ENVIRONMENT_NAME,
            account=account,
            region=region,
            domain_name=inputs.domain_name,
            hosted_zone_id=inputs.hosted_zone_id,
            image_tag=inputs.image_tag,
            alert_email=inputs.alert_email,
            cloudfront_prefix_list_id=inputs.cloudfront_prefix_list_id,
        ),
    )
    app.synth()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
