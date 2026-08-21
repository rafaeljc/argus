from collections.abc import Mapping
from typing import Any

import pytest
from aws_cdk import App
from aws_cdk import aws_cloudfront as cloudfront
from aws_cdk.assertions import Match, Template

from argus.config import EnvironmentConfig
from argus.stacks.compute import ComputeStack
from argus.stacks.data import DataStack
from argus.stacks.edge import EdgeStack
from argus.stacks.network import NetworkStack

Resource = Mapping[str, Any]

BUCKET_NAME = "argus-prod-frontend-123456789012"


@pytest.fixture(scope="module")
def stack(config: EnvironmentConfig) -> EdgeStack:
    app = App()
    network = NetworkStack(app, "argus-prod-network", config=config)
    data = DataStack(app, "argus-prod-data", config=config, vpc=network.vpc)
    compute = ComputeStack(
        app,
        "argus-prod-compute",
        config=config,
        vpc=network.vpc,
        database=data.database,
        connection_secret=data.connection_secret,
    )
    return EdgeStack(
        app, "argus-prod-edge", config=config, load_balancer=compute.backend.load_balancer
    )


@pytest.fixture(scope="module")
def template(stack: EdgeStack) -> Template:
    return Template.from_stack(stack)


@pytest.fixture(scope="module")
def distribution(template: Template) -> Resource:
    config: Resource = _only_resource(template, "AWS::CloudFront::Distribution")["Properties"][
        "DistributionConfig"
    ]
    return config


# --- the bundle ----------------------------------------------------------------------------


def test_the_bucket_name_is_qualified_by_account(template: Template) -> None:
    # Bucket names are global; an unqualified one could already be taken.
    template.has_resource_properties("AWS::S3::Bucket", {"BucketName": BUCKET_NAME})


def test_the_bucket_is_closed_to_the_public(template: Template) -> None:
    template.has_resource_properties(
        "AWS::S3::Bucket",
        {
            "PublicAccessBlockConfiguration": {
                "BlockPublicAcls": True,
                "BlockPublicPolicy": True,
                "IgnorePublicAcls": True,
                "RestrictPublicBuckets": True,
            }
        },
    )


def test_the_bucket_refuses_plaintext_requests(template: Template) -> None:
    statements = _only_resource(template, "AWS::S3::BucketPolicy")["Properties"]["PolicyDocument"][
        "Statement"
    ]

    assert any(
        statement["Effect"] == "Deny"
        and statement["Condition"].get("Bool", {}).get("aws:SecureTransport") == "false"
        for statement in statements
    )


def test_the_bucket_outlives_its_stack(template: Template) -> None:
    # CloudFront points at it by name, and the contents are re-uploadable but
    # the bucket itself is referenced from outside this stack.
    template.has_resource(
        "AWS::S3::Bucket", {"DeletionPolicy": "Retain", "UpdateReplacePolicy": "Retain"}
    )


def test_only_cloudfront_can_read_the_bucket(template: Template) -> None:
    statements = _only_resource(template, "AWS::S3::BucketPolicy")["Properties"]["PolicyDocument"][
        "Statement"
    ]

    allowed = [statement for statement in statements if statement["Effect"] == "Allow"]
    assert allowed
    for statement in allowed:
        assert statement["Principal"] == {"Service": "cloudfront.amazonaws.com"}


# --- how requests are routed ---------------------------------------------------------------


def test_the_site_answers_on_the_configured_domain(distribution: Resource) -> None:
    assert distribution["Aliases"] == ["argusapp.click"]


def test_plaintext_visitors_are_redirected_to_https(distribution: Resource) -> None:
    behaviors = [distribution["DefaultCacheBehavior"], *distribution["CacheBehaviors"]]

    for behavior in behaviors:
        assert behavior["ViewerProtocolPolicy"] == "redirect-to-https"


def test_client_side_routes_are_served_the_entry_point(distribution: Resource) -> None:
    associations = distribution["DefaultCacheBehavior"]["FunctionAssociations"]

    assert [association["EventType"] for association in associations] == ["viewer-request"]


def test_the_api_is_routed_to_the_backend_not_the_bucket(distribution: Resource) -> None:
    api = _behavior_for(distribution, "/api/*")

    assert api["TargetOriginId"] != distribution["DefaultCacheBehavior"]["TargetOriginId"]


def test_the_api_reaches_the_load_balancer_privately(template: Template) -> None:
    # A VPC origin, so the load balancer never needs a public address.
    template.resource_count_is("AWS::CloudFront::VpcOrigin", 1)


def test_the_api_accepts_every_method_the_backend_implements(distribution: Resource) -> None:
    api = _behavior_for(distribution, "/api/*")

    assert set(api["AllowedMethods"]) == {
        "GET",
        "HEAD",
        "OPTIONS",
        "PUT",
        "PATCH",
        "POST",
        "DELETE",
    }


def test_api_responses_are_never_cached(distribution: Resource) -> None:
    api = _behavior_for(distribution, "/api/*")

    assert api["CachePolicyId"] == cloudfront.CachePolicy.CACHING_DISABLED.cache_policy_id


def test_the_backend_receives_the_original_request_apart_from_the_host(
    distribution: Resource,
) -> None:
    api = _behavior_for(distribution, "/api/*")
    expected = cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER

    assert api["OriginRequestPolicyId"] == expected.origin_request_policy_id


# --- transport security --------------------------------------------------------------------


def test_obsolete_tls_versions_are_refused(distribution: Resource) -> None:
    assert distribution["ViewerCertificate"]["MinimumProtocolVersion"] == "TLSv1.2_2021"


def test_the_certificate_is_validated_through_the_hosted_zone(template: Template) -> None:
    template.has_resource_properties(
        "AWS::CertificateManager::Certificate",
        Match.object_like({"DomainName": "argusapp.click", "ValidationMethod": "DNS"}),
    )


def test_browsers_are_told_never_to_use_plaintext_again(template: Template) -> None:
    hsts = _security_headers(template)["StrictTransportSecurity"]

    assert hsts["AccessControlMaxAgeSec"] == 31536000
    assert hsts["IncludeSubdomains"] is True


def test_the_domain_is_not_committed_to_the_browser_preload_list(template: Template) -> None:
    """Preload is close to irreversible, so it is a later decision, not a default."""
    hsts = _security_headers(template)["StrictTransportSecurity"]

    assert hsts.get("Preload", False) is False


def test_the_usual_browser_side_protections_are_set(template: Template) -> None:
    policy = _security_headers(template)

    assert policy["ContentTypeOptions"]["Override"] is True
    assert policy["FrameOptions"]["FrameOption"] == "DENY"
    assert policy["ReferrerPolicy"]["ReferrerPolicy"] == "strict-origin-when-cross-origin"


# --- reaching the site ---------------------------------------------------------------------


def test_the_apex_domain_points_at_the_distribution(template: Template) -> None:
    template.has_resource_properties(
        "AWS::Route53::RecordSet",
        Match.object_like({"Name": "argusapp.click.", "Type": "A"}),
    )


# --- deployment ----------------------------------------------------------------------------


def test_the_frontend_role_may_publish_a_bundle_and_invalidate_the_cache(
    template: Template,
) -> None:
    granted = {
        action
        for policy in template.find_resources("AWS::IAM::Policy").values()
        for statement in policy["Properties"]["PolicyDocument"]["Statement"]
        for action in _as_list(statement["Action"])
    }

    # CDK's grants use wildcard action names, so match on the operation.
    def may(operation: str) -> bool:
        return any(action.rstrip("*") == operation for action in granted)

    assert may("cloudfront:CreateInvalidation")
    assert may("s3:PutObject")
    assert may("s3:DeleteObject")


def test_the_frontend_workflow_can_find_what_it_deploys_to(template: Template) -> None:
    published = {
        parameter["Properties"]["Name"]
        for parameter in template.find_resources("AWS::SSM::Parameter").values()
    }

    assert published == {
        "/argus/prod/out/frontend/bucket-name",
        "/argus/prod/out/frontend/distribution-id",
    }


def _only_resource(template: Template, resource_type: str) -> Resource:
    resources = template.find_resources(resource_type)
    assert len(resources) == 1, f"expected one {resource_type}, got {list(resources)}"
    resource: Resource = next(iter(resources.values()))
    return resource


def _security_headers(template: Template) -> Resource:
    headers: Resource = _only_resource(template, "AWS::CloudFront::ResponseHeadersPolicy")[
        "Properties"
    ]["ResponseHeadersPolicyConfig"]["SecurityHeadersConfig"]
    return headers


def _behavior_for(distribution: Resource, path: str) -> Resource:
    matching = [
        behavior for behavior in distribution["CacheBehaviors"] if behavior["PathPattern"] == path
    ]
    assert len(matching) == 1, f"expected one behavior for {path}"
    behavior: Resource = matching[0]
    return behavior


def _as_list(action: Any) -> list[str]:
    return [action] if isinstance(action, str) else list(action)
