def test_management_port_is_not_exposed(template):
    # 8081 serves unauthenticated actuator (SecurityConfig.java:42) — it must never
    # appear on a listener, only on the health check.
    for listener in template.find_resources("AWS::ElasticLoadBalancingV2::Listener").values():
        assert listener["Properties"]["Port"] in (80, 443)


def test_no_plaintext_config_in_task_definition(template):
    (task_def,) = template.find_resources("AWS::ECS::TaskDefinition").values()
    (container,) = task_def["Properties"]["ContainerDefinitions"]
    assert container["Environment"] == [
        {"Name": "SPRING_PROFILES_ACTIVE", "Value": "prod"},
    ]


def test_container_secret_names_match_the_app_contract(template):
    # This is the CDK half of the required-configuration contract; the application half is
    # ConfigContractTest (backend/src/test/java/.../config/ConfigContractTest.java). Neither test
    # can see the other's build, so this set is frozen here rather than derived.
    (task_def,) = template.find_resources("AWS::ECS::TaskDefinition").values()
    (container,) = task_def["Properties"]["ContainerDefinitions"]
    secret_names = {secret["Name"] for secret in container["Secrets"]}
    assert secret_names == {
        "ARGUS_DB_HOST",
        "ARGUS_DB_PORT",
        "ARGUS_DB_NAME",
        "ARGUS_DB_USERNAME",
        "ARGUS_DB_PASSWORD",
        "ARGUS_APP_BASE_URL",
        "ARGUS_EMAIL_ADDRESS",
        "ARGUS_EMAIL_RESEND_API_URL",
        "ARGUS_EMAIL_RESEND_API_KEY",
        "ARGUS_MARKETDATA_MASSIVE_API_URL",
        "ARGUS_MARKETDATA_MASSIVE_API_KEY",
    }


def test_task_has_no_public_ip(template):
    (service,) = template.find_resources("AWS::ECS::Service").values()
    network_config = service["Properties"]["NetworkConfiguration"]["AwsvpcConfiguration"]
    assert network_config["AssignPublicIp"] == "DISABLED"


def test_health_check_targets_management_port_over_traffic_port(template):
    (target_group,) = template.find_resources("AWS::ElasticLoadBalancingV2::TargetGroup").values()
    props = target_group["Properties"]
    assert props["HealthCheckPort"] == "8081"
    assert props["HealthCheckPath"] == "/actuator/health/readiness"

    (service,) = template.find_resources("AWS::ECS::Service").values()
    (load_balancer,) = service["Properties"]["LoadBalancers"]
    assert load_balancer["ContainerPort"] == 8080


def test_management_port_is_mapped_on_the_container(template):
    (task_def,) = template.find_resources("AWS::ECS::TaskDefinition").values()
    (container,) = task_def["Properties"]["ContainerDefinitions"]
    ports = {mapping["ContainerPort"] for mapping in container["PortMappings"]}
    assert 8081 in ports


def test_management_port_ingress_is_restricted_to_the_alb(template):
    # The ALBFargateService pattern only opens an ingress rule for the traffic
    # port (8080). 8081 needs an explicit rule scoped to the ALB's security
    # group — otherwise the health check silently fails.
    ingress_rules = template.find_resources("AWS::EC2::SecurityGroupIngress")
    matches = [
        rule
        for rule in ingress_rules.values()
        if rule["Properties"].get("FromPort") == 8081 and rule["Properties"].get("ToPort") == 8081
    ]
    assert len(matches) == 1
    assert "SourceSecurityGroupId" in matches[0]["Properties"]
