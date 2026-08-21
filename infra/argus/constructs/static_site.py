"""The bucket holding the frontend bundle, and the headers every response carries.

The bucket is closed: no public access, no website endpoint, and a policy that
names CloudFront's service principal as the only reader. Requests reach the
objects through an origin access control, so there is no URL that serves the
bundle directly.
"""

from pathlib import Path

from aws_cdk import Duration
from aws_cdk import aws_cloudfront as cloudfront
from aws_cdk import aws_s3 as s3
from constructs import Construct

from argus.config import EnvironmentConfig
from argus.retention import Durability

HSTS_MAX_AGE = Duration.days(365)
_FUNCTION_SOURCE = Path(__file__).parent.parent / "functions" / "spa_rewrite.js"


def frontend_bucket(scope: Construct, config: EnvironmentConfig) -> s3.Bucket:
    """The bundle's home. Retained, because CloudFront names it as an origin."""
    return s3.Bucket(
        scope,
        "FrontendBucket",
        bucket_name=config.naming.frontend_bucket(config.account),
        block_public_access=s3.BlockPublicAccess.BLOCK_ALL,
        enforce_ssl=True,
        encryption=s3.BucketEncryption.S3_MANAGED,
        removal_policy=Durability.RETAINED.removal_policy,
        # No auto_delete_objects: it would add a custom-resource Lambda whose
        # only job is emptying a bucket that is deliberately retained.
        auto_delete_objects=False,
    )


def spa_rewrite_function(scope: Construct, config: EnvironmentConfig) -> cloudfront.Function:
    """Rewrites client-side routes to the entry point before the origin is asked.

    The source is read at synth and inlined into the template, so this ships no
    asset and needs no bundling step.
    """
    return cloudfront.Function(
        scope,
        "SpaRewrite",
        function_name=config.naming.resource("spa-rewrite"),
        runtime=cloudfront.FunctionRuntime.JS_2_0,
        code=cloudfront.FunctionCode.from_file(file_path=str(_FUNCTION_SOURCE)),
        comment="Serves index.html for paths that name a route rather than a file",
    )


def security_headers(
    scope: Construct, config: EnvironmentConfig
) -> cloudfront.ResponseHeadersPolicy:
    """Response headers required of a deployed environment by docs/NFR.md."""
    return cloudfront.ResponseHeadersPolicy(
        scope,
        "SecurityHeaders",
        response_headers_policy_name=config.naming.resource("security-headers"),
        security_headers_behavior=cloudfront.ResponseSecurityHeadersBehavior(
            strict_transport_security=cloudfront.ResponseHeadersStrictTransportSecurity(
                access_control_max_age=HSTS_MAX_AGE,
                include_subdomains=True,
                preload=True,
                override=True,
            ),
            content_type_options=cloudfront.ResponseHeadersContentTypeOptions(override=True),
            frame_options=cloudfront.ResponseHeadersFrameOptions(
                frame_option=cloudfront.HeadersFrameOption.DENY, override=True
            ),
            referrer_policy=cloudfront.ResponseHeadersReferrerPolicy(
                referrer_policy=cloudfront.HeadersReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
                override=True,
            ),
        ),
    )
