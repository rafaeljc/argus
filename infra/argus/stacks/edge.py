"""What the internet actually talks to.

One distribution serves both halves of the product: the bundle from S3, and
everything under /api/* from the backend. That is what lets the browser treat
the API as same-origin, so there is no CORS preflight on ordinary requests and
session cookies are first-party.

The backend is reached through a VPC origin, so the load balancer keeps no
public address and its security group admits only CloudFront's prefix list.
"""

from aws_cdk import aws_certificatemanager as acm
from aws_cdk import aws_cloudfront as cloudfront
from aws_cdk import aws_cloudfront_origins as origins
from aws_cdk import aws_elasticloadbalancingv2 as elbv2
from aws_cdk import aws_iam as iam
from aws_cdk import aws_route53 as route53
from aws_cdk import aws_route53_targets as targets
from constructs import Construct

from argus.config import EnvironmentConfig
from argus.constructs.static_site import frontend_bucket, security_headers, spa_rewrite_function
from argus.stacks.base import ArgusStack

API_PATH_PATTERN = "/api/*"


class EdgeStack(ArgusStack):
    """Certificate, distribution, DNS, and the access the frontend workflow needs."""

    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        config: EnvironmentConfig,
        load_balancer: elbv2.IApplicationLoadBalancer,
    ) -> None:
        super().__init__(scope, construct_id, config=config)

        self.bucket = frontend_bucket(self, config)

        # Adopted by id rather than discovered by a context lookup, so a synth
        # never depends on a cached cdk.context.json.
        self.hosted_zone = route53.HostedZone.from_hosted_zone_attributes(
            self,
            "HostedZone",
            hosted_zone_id=config.hosted_zone_id,
            zone_name=config.domain_name,
        )

        self.distribution = self._distribution(config, load_balancer)
        self._point_the_domain_at_the_distribution(config)
        self._allow_frontend_deployments(config)

        discovery = self.discovery
        discovery.publish(
            "frontend",
            "bucket-name",
            self.bucket.bucket_name,
            "Bucket the frontend bundle is uploaded to",
        )
        discovery.publish(
            "frontend",
            "distribution-id",
            self.distribution.distribution_id,
            "Distribution to invalidate after a bundle is uploaded",
        )

    def _distribution(
        self, config: EnvironmentConfig, load_balancer: elbv2.IApplicationLoadBalancer
    ) -> cloudfront.Distribution:
        certificate = acm.Certificate(
            self,
            "Certificate",
            domain_name=config.domain_name,
            validation=acm.CertificateValidation.from_dns(self.hosted_zone),
        )
        headers = security_headers(self, config)

        return cloudfront.Distribution(
            self,
            "Distribution",
            domain_names=[config.domain_name],
            certificate=certificate,
            minimum_protocol_version=cloudfront.SecurityPolicyProtocol.TLS_V1_2_2021,
            default_root_object="index.html",
            comment=f"Argus {config.name}",
            default_behavior=cloudfront.BehaviorOptions(
                origin=origins.S3BucketOrigin.with_origin_access_control(self.bucket),
                viewer_protocol_policy=cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,
                response_headers_policy=headers,
                function_associations=[
                    cloudfront.FunctionAssociation(
                        function=spa_rewrite_function(self, config),
                        event_type=cloudfront.FunctionEventType.VIEWER_REQUEST,
                    )
                ],
            ),
            additional_behaviors={
                API_PATH_PATTERN: cloudfront.BehaviorOptions(
                    origin=origins.VpcOrigin.with_application_load_balancer(
                        load_balancer,
                        protocol_policy=cloudfront.OriginProtocolPolicy.HTTP_ONLY,
                        vpc_origin_name=config.naming.resource("backend-origin"),
                    ),
                    viewer_protocol_policy=cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                    allowed_methods=cloudfront.AllowedMethods.ALLOW_ALL,
                    # An API response is specific to its caller's session; a
                    # cache hit here would serve one user another user's data.
                    cache_policy=cloudfront.CachePolicy.CACHING_DISABLED,
                    # Everything except Host, which has to stay the origin's own
                    # or the load balancer will not route the request.
                    origin_request_policy=(
                        cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER
                    ),
                    response_headers_policy=headers,
                ),
            },
        )

    def _point_the_domain_at_the_distribution(self, config: EnvironmentConfig) -> None:
        route53.ARecord(
            self,
            "ApexRecord",
            zone=self.hosted_zone,
            record_name=config.domain_name,
            target=route53.RecordTarget.from_alias(targets.CloudFrontTarget(self.distribution)),
        )

    def _allow_frontend_deployments(self, config: EnvironmentConfig) -> None:
        role = iam.Role.from_role_name(
            self, "FrontendDeployRole", config.naming.resource("cd", "frontend")
        )
        # Delete included: the workflow syncs with --delete so a removed asset
        # does not linger in the bucket forever.
        self.bucket.grant_read_write(role)
        self.bucket.grant_delete(role)
        role.add_to_principal_policy(
            iam.PolicyStatement(
                actions=["cloudfront:CreateInvalidation"],
                resources=[
                    self.format_arn(
                        service="cloudfront",
                        region="",
                        resource="distribution",
                        resource_name=self.distribution.distribution_id,
                    )
                ],
            )
        )
