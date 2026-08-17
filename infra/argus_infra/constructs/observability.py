from aws_cdk import CfnOutput, Duration
from aws_cdk import aws_cloudwatch as cloudwatch
from aws_cdk import aws_cloudwatch_actions as cw_actions
from aws_cdk import aws_elasticloadbalancingv2 as elbv2
from aws_cdk import aws_logs as logs
from aws_cdk import aws_rds as rds
from aws_cdk import aws_sns as sns
from constructs import Construct

TWO_GIB_IN_BYTES = 2 * 1024**3


class Observability(Construct):
    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        target_group: elbv2.IApplicationTargetGroup,
        log_group: logs.ILogGroup,
        database: rds.DatabaseInstance,
    ) -> None:
        super().__init__(scope, construct_id)

        self.topic = sns.Topic(self, "Alarms")

        alarms = [
            # 1. The task is down or failing its health check. desired_count=1, so
            # this is "the API is off".
            target_group.metric_healthy_host_count().create_alarm(
                self,
                "NoHealthyTask",
                threshold=1,
                evaluation_periods=2,
                comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
                treat_missing_data=cloudwatch.TreatMissingData.BREACHING,
            ),
            # 2. The task is healthy but the application is throwing.
            target_group.metric_http_code_target(
                elbv2.HttpCodeTarget.TARGET_5XX_COUNT,
                statistic="Sum",
                period=Duration.minutes(5),
            ).create_alarm(
                self,
                "Target5xx",
                threshold=10,
                evaluation_periods=1,
                treat_missing_data=cloudwatch.TreatMissingData.NOT_BREACHING,
            ),
            # 3. Errors that never become an HTTP 5xx: the outbox poller, the EOD
            # scheduler, Flyway. These swallow vendor failures by design and never
            # produce an HTTP status, so this filter is the only thing that sees them.
            logs.MetricFilter(
                self,
                "ErrorLogs",
                log_group=log_group,
                filter_pattern=logs.FilterPattern.string_value("$.level", "=", "ERROR"),
                metric_namespace="Argus",
                metric_name="ErrorLogCount",
                metric_value="1",
                default_value=0,
            )
            .metric(period=Duration.minutes(5), statistic="Sum")
            .create_alarm(
                self,
                "ErrorLogAlarm",
                threshold=1,
                evaluation_periods=1,
                comparison_operator=cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
                treat_missing_data=cloudwatch.TreatMissingData.NOT_BREACHING,
            ),
            # 4. The classic silent killer on a 20 GiB volume.
            database.metric_free_storage_space().create_alarm(
                self,
                "DbDiskLow",
                threshold=TWO_GIB_IN_BYTES,
                evaluation_periods=1,
                comparison_operator=cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
            ),
        ]

        for alarm in alarms:
            alarm.add_alarm_action(cw_actions.SnsAction(self.topic))
            alarm.add_ok_action(cw_actions.SnsAction(self.topic))

        CfnOutput(self, "AlarmTopicArn", value=self.topic.topic_arn)
