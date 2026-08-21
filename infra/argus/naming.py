"""Resource and parameter names.

Two rules govern every name in this project:

1. Component vocabulary comes from the repository. The tree says ``backend/`` and
   ``frontend/``, so the infrastructure says ``backend`` and ``frontend`` -- never
   ``web``, ``app``, ``api`` or ``site`` as a synonym.
2. Everything carries its environment: ``argus-prod-...``. There is exactly one
   exception, :data:`BACKEND_REPOSITORY_NAME`, documented below.
"""

from dataclasses import dataclass

# The one name without an environment: a container image is built once and
# promoted *across* environments, so the repository holding it belongs to none.
BACKEND_REPOSITORY_NAME = "argus-backend"

_PREFIX = "argus"
_OUTPUT_SUBTREE = "out"


@dataclass(frozen=True)
class Naming:
    """Builds every name and parameter path for a single environment."""

    environment: str

    def resource(self, *parts: str) -> str:
        """``argus-prod-cd-backend`` for ``resource("cd", "backend")``."""
        return "-".join((_PREFIX, self.environment, *parts))

    def frontend_bucket(self, account: str) -> str:
        """The frontend bucket name, qualified by account.

        S3 bucket names are globally unique across all of AWS, so an unqualified
        ``argus-prod-frontend`` risks colliding with a bucket in someone else's
        account. Every other name here is account-scoped and needs no suffix.
        """
        return self.resource("frontend", account)

    def input_parameter(self, *parts: str) -> str:
        """Path of a hand-managed input, e.g. ``/argus/prod/env/app-base-url``."""
        return "/".join(("", _PREFIX, self.environment, *parts))

    def output_parameter(self, component: str, name: str) -> str:
        """Path of a CDK-written discovery value, e.g. ``/argus/prod/out/backend/cluster-name``.

        Outputs are keyed by component rather than by AWS service, so each
        deployment workflow reads exactly one prefix.
        """
        return self.input_parameter(_OUTPUT_SUBTREE, component, name)
