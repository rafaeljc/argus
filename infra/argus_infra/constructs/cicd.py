from aws_cdk import CfnOutput, Stack
from aws_cdk import aws_cloudfront as cloudfront
from aws_cdk import aws_ecr as ecr
from aws_cdk import aws_ecs as ecs
from aws_cdk import aws_iam as iam
from aws_cdk import aws_s3 as s3
from constructs import Construct

GITHUB_OIDC_HOST = "token.actions.githubusercontent.com"
GITHUB_REPO_SUB = "repo:rafaeljc/argus:ref:refs/heads/main"


class Cicd(Construct):
    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        repository: ecr.IRepository,
        service: ecs.IBaseService,
        task_execution_role: iam.IRole,
        task_role: iam.IRole,
        bucket: s3.IBucket,
        distribution: cloudfront.IDistribution,
        image_tag_parameter_name: str,
    ) -> None:
        super().__init__(scope, construct_id)

        stack = Stack.of(self)

        provider = iam.OpenIdConnectProvider(
            self,
            "GithubOidcProvider",
            url=f"https://{GITHUB_OIDC_HOST}",
            client_ids=["sts.amazonaws.com"],
        )
        github_actions_main = iam.OpenIdConnectPrincipal(
            provider,
            conditions={
                "StringEquals": {
                    f"{GITHUB_OIDC_HOST}:aud": "sts.amazonaws.com",
                    f"{GITHUB_OIDC_HOST}:sub": GITHUB_REPO_SUB,
                }
            },
        )

        stack_arn = stack.format_arn(service="cloudformation", resource="stack", resource_name=f"{stack.stack_name}/*")
        image_tag_param_arn = stack.format_arn(
            service="ssm",
            resource="parameter",
            resource_name=image_tag_parameter_name.lstrip("/"),
        )

        self.backend_role = iam.Role(self, "BackendDeployRole", assumed_by=github_actions_main)
        self.backend_role.add_to_policy(
            iam.PolicyStatement(actions=["ecr:GetAuthorizationToken"], resources=["*"])
        )
        self.backend_role.add_to_policy(
            iam.PolicyStatement(
                actions=[
                    "ecr:BatchCheckLayerAvailability",
                    "ecr:InitiateLayerUpload",
                    "ecr:UploadLayerPart",
                    "ecr:CompleteLayerUpload",
                    "ecr:PutImage",
                    "ecr:BatchGetImage",
                    "ecr:DescribeImages",
                ],
                resources=[repository.repository_arn],
            )
        )
        self.backend_role.add_to_policy(
            # RegisterTaskDefinition and DescribeTaskDefinition do not support resource-level
            # permissions in IAM - AWS requires "*" for these two actions.
            iam.PolicyStatement(
                actions=["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"],
                resources=["*"],
            )
        )
        self.backend_role.add_to_policy(
            iam.PolicyStatement(
                actions=["ecs:UpdateService", "ecs:DescribeServices"],
                resources=[service.service_arn],
            )
        )
        self.backend_role.add_to_policy(
            iam.PolicyStatement(
                actions=["ssm:GetParameter", "ssm:PutParameter"],
                resources=[image_tag_param_arn],
            )
        )
        self.backend_role.add_to_policy(
            iam.PolicyStatement(actions=["cloudformation:DescribeStacks"], resources=[stack_arn])
        )
        self.backend_role.add_to_policy(
            iam.PolicyStatement(
                actions=["iam:PassRole"],
                resources=[task_execution_role.role_arn, task_role.role_arn],
            )
        )

        self.frontend_role = iam.Role(self, "FrontendDeployRole", assumed_by=github_actions_main)
        self.frontend_role.add_to_policy(
            iam.PolicyStatement(actions=["s3:ListBucket"], resources=[bucket.bucket_arn])
        )
        self.frontend_role.add_to_policy(
            iam.PolicyStatement(
                actions=["s3:PutObject", "s3:DeleteObject"],
                resources=[bucket.arn_for_objects("*")],
            )
        )
        self.frontend_role.add_to_policy(
            iam.PolicyStatement(
                actions=["cloudfront:CreateInvalidation"],
                resources=[
                    stack.format_arn(
                        service="cloudfront",
                        region="",
                        resource="distribution",
                        resource_name=distribution.distribution_id,
                    )
                ],
            )
        )
        self.frontend_role.add_to_policy(
            iam.PolicyStatement(actions=["cloudformation:DescribeStacks"], resources=[stack_arn])
        )

        CfnOutput(self, "BackendDeployRoleArn", value=self.backend_role.role_arn)
        CfnOutput(self, "FrontendDeployRoleArn", value=self.frontend_role.role_arn)
