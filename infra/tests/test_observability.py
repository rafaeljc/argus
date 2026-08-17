from aws_cdk.assertions import Match


def _alarm_by_logical_id_substring(template, substring):
    alarms = template.find_resources("AWS::CloudWatch::Alarm")
    matches = {k: v for k, v in alarms.items() if substring in k}
    assert len(matches) == 1, f"expected exactly one alarm matching {substring!r}, got {matches.keys()}"
    return next(iter(matches.values()))


def test_sns_topic_exists(template):
    template.resource_count_is("AWS::SNS::Topic", 1)


def test_four_alarms_each_notify_on_alarm_and_recovery(template):
    alarms = template.find_resources("AWS::CloudWatch::Alarm")
    assert len(alarms) == 4
    for alarm in alarms.values():
        props = alarm["Properties"]
        assert props.get("AlarmActions"), f"{alarm} missing AlarmActions"
        assert props.get("OKActions"), f"{alarm} missing OKActions"


def test_no_healthy_task_alarm_breaches_on_missing_data(template):
    alarm = _alarm_by_logical_id_substring(template, "NoHealthyTask")
    assert alarm["Properties"]["TreatMissingData"] == "breaching"
    assert alarm["Properties"]["ComparisonOperator"] == "LessThanThreshold"
    assert alarm["Properties"]["Threshold"] == 1


def test_error_log_metric_filter_matches_encoder_level_field(template):
    template.has_resource_properties(
        "AWS::Logs::MetricFilter",
        {
            "FilterPattern": '{ $.level = "ERROR" }',
            "MetricTransformations": Match.array_with(
                [Match.object_like({"MetricNamespace": "Argus", "MetricName": "ErrorLogCount"})]
            ),
        },
    )


def test_db_disk_low_alarm_thresholds_at_two_gib(template):
    alarm = _alarm_by_logical_id_substring(template, "DbDiskLow")
    assert alarm["Properties"]["Threshold"] == 2 * 1024**3
    assert alarm["Properties"]["ComparisonOperator"] == "LessThanThreshold"
