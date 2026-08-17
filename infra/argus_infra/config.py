from dataclasses import dataclass

from aws_cdk import aws_logs as logs


@dataclass(frozen=True)
class ArgusEnv:
    name: str
    spring_profile: str
    task_cpu: int
    task_memory_mib: int
    db_instance_class: str
    log_retention: logs.RetentionDays


ENVIRONMENTS = {
    "prod": ArgusEnv(
        name="prod",
        spring_profile="prod",
        task_cpu=1024,
        task_memory_mib=2048,
        db_instance_class="db.t4g.micro",
        log_retention=logs.RetentionDays.ONE_MONTH,
    ),
}
