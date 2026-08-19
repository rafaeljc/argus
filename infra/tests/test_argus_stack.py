from aws_cdk.assertions import Match


def test_task_definition_image_references_the_ssm_image_tag_parameter(template):
    # CDK provisions infra only; it never names an image tag. The task definition's
    # Image must resolve the tag from the CFN parameter CloudFormation binds to
    # /argus/prod/image-tag at deploy time, not a literal string CDK chose at synth time.
    (ssm_param_id,) = (
        logical_id
        for logical_id, param in template.to_json()["Parameters"].items()
        if param.get("Default") == "/argus/prod/image-tag"
    )
    assert template.to_json()["Parameters"][ssm_param_id]["Type"] == "AWS::SSM::Parameter::Value<String>"

    template.has_resource_properties(
        "AWS::ECS::TaskDefinition",
        {
            "ContainerDefinitions": Match.array_with(
                [
                    Match.object_like(
                        {
                            "Image": {
                                "Fn::Join": Match.array_with(
                                    [Match.array_with([{"Ref": ssm_param_id}])]
                                )
                            }
                        }
                    )
                ]
            )
        },
    )


def test_certificate_covers_apex_and_api_hostname(template):
    template.has_resource_properties(
        "AWS::CertificateManager::Certificate",
        {"ValidationMethod": "DNS"},
    )
    certs = template.find_resources("AWS::CertificateManager::Certificate")
    assert len(certs) == 1
    (cert,) = certs.values()
    assert len(cert["Properties"]["SubjectAlternativeNames"]) == 2
