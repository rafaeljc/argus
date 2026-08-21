"""Immutable configuration for one deployment environment."""

from dataclasses import dataclass

from argus.naming import Naming


@dataclass(frozen=True)
class EnvironmentConfig:
    """Everything the stacks need to know, resolved once at synth time."""

    name: str
    account: str
    region: str
    domain_name: str
    hosted_zone_id: str
    image_tag: str
    alert_email: str
    cloudfront_prefix_list_id: str
    github_repository: str = "rafaeljc/argus"
    github_branch: str = "main"

    @property
    def naming(self) -> Naming:
        return Naming(environment=self.name)


@dataclass(frozen=True)
class BackendSizing:
    """Task shape for the backend service."""

    cpu: int = 1024
    memory_mib: int = 2048
    traffic_port: int = 8080
    management_port: int = 8081
    log_retention_days: int = 30
    readiness_path: str = "/actuator/health/ready"


@dataclass(frozen=True)
class DatabaseSizing:
    """Shape of the Postgres instance."""

    instance_class: str = "t4g.micro"
    allocated_storage_gib: int = 20
    backup_retention_days: int = 14
    database_name: str = "argus"
    username: str = "argus"
    port: int = 5432


BACKEND = BackendSizing()
DATABASE = DatabaseSizing()
