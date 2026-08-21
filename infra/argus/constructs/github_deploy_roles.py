"""The identities GitHub Actions assumes to deploy.

Federated through OIDC, so no long-lived access key exists anywhere. Each role
starts with nothing but its trust policy; the stack that owns a resource grants
the access to it, which keeps permissions least-privilege without forcing every
stack to be deployed in one pass.
"""

from aws_cdk import aws_iam as iam
from constructs import Construct

from argus.config import EnvironmentConfig

GITHUB_ISSUER = "token.actions.githubusercontent.com"
STS_AUDIENCE = "sts.amazonaws.com"


class GitHubDeployRoles(Construct):
    """One role per deployable component, assumable only by this repository."""

    def __init__(self, scope: Construct, construct_id: str, *, config: EnvironmentConfig) -> None:
        super().__init__(scope, construct_id)

        # The one resource here that is account-scoped rather than
        # environment-scoped: AWS permits a single provider per issuer URL per
        # account. A second environment must import this one, not create another.
        provider = iam.OpenIdConnectProvider(
            self,
            "GitHubProvider",
            url=f"https://{GITHUB_ISSUER}",
            client_ids=[STS_AUDIENCE],
        )

        self.backend = self._deploy_role(provider, config, "backend")
        self.frontend = self._deploy_role(provider, config, "frontend")

    def _deploy_role(
        self,
        provider: iam.IOpenIdConnectProvider,
        config: EnvironmentConfig,
        component: str,
    ) -> iam.Role:
        subject = f"repo:{config.github_repository}:ref:refs/heads/{config.github_branch}"
        return iam.Role(
            self,
            f"{component.capitalize()}Role",
            role_name=config.naming.resource("cd", component),
            description=f"Deploys the {component} to {config.name} from GitHub Actions",
            assumed_by=iam.OpenIdConnectPrincipal(
                provider,
                # Both conditions matter: without the subject any repository
                # could assume the role, and without the audience a token minted
                # for another service would be accepted.
                {
                    "StringEquals": {
                        f"{GITHUB_ISSUER}:aud": STS_AUDIENCE,
                        f"{GITHUB_ISSUER}:sub": subject,
                    }
                },
            ),
        )
