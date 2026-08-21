"""Alarms that are worth waking someone for.

Every alarm in this project is critical by construction: if it is not worth an
email at 3am it is a dashboard metric, not an alarm. They all deliver to the one
topic created by the foundation stack.

That topic is reached through its deterministic ARN rather than a
CloudFormation export. An export would pin the topic in place -- a stack cannot
delete a resource another stack imports -- and would force the foundation stack
to be updated in lockstep with everything downstream of it.
"""

from aws_cdk import Duration, Stack
from aws_cdk import aws_cloudwatch as cloudwatch
from aws_cdk import aws_cloudwatch_actions as cloudwatch_actions
from aws_cdk import aws_sns as sns
from constructs import Construct

from argus.naming import Naming

DEFAULT_EVALUATION_PERIODS = 2


class CriticalAlarms:
    """Creates alarms for one stack, all pointed at the environment's topic."""

    def __init__(self, scope: Construct, naming: Naming) -> None:
        self._scope = scope
        self._naming = naming
        self._action = cloudwatch_actions.SnsAction(
            sns.Topic.from_topic_arn(
                scope,
                "ImportedAlarmTopic",
                Stack.of(scope).format_arn(service="sns", resource=naming.resource("alarms")),
            )
        )

    def add(
        self,
        name: str,
        *,
        metric: cloudwatch.IMetric,
        threshold: float,
        description: str,
        evaluation_periods: int = DEFAULT_EVALUATION_PERIODS,
        comparison_operator: cloudwatch.ComparisonOperator = (
            cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD
        ),
        treat_missing_data: cloudwatch.TreatMissingData = (
            cloudwatch.TreatMissingData.NOT_BREACHING
        ),
    ) -> cloudwatch.Alarm:
        alarm = cloudwatch.Alarm(
            self._scope,
            _construct_id(name),
            alarm_name=self._naming.resource(name),
            alarm_description=description,
            metric=metric,
            threshold=threshold,
            evaluation_periods=evaluation_periods,
            comparison_operator=comparison_operator,
            treat_missing_data=treat_missing_data,
        )
        alarm.add_alarm_action(self._action)
        return alarm


def instance_status_check(instance_id: str) -> cloudwatch.Metric:
    """Fails when EC2 or the guest OS stops answering for an instance."""
    return cloudwatch.Metric(
        namespace="AWS/EC2",
        metric_name="StatusCheckFailed",
        dimensions_map={"InstanceId": instance_id},
        statistic="Maximum",
        period=Duration.minutes(1),
    )


def _construct_id(name: str) -> str:
    return "".join(word.capitalize() for word in name.split("-")) + "Alarm"
