from aws_cdk.assertions import Match


def test_ecr_repository_has_immutable_tags(template):
    template.has_resource_properties(
        "AWS::ECR::Repository",
        {"ImageTagMutability": "IMMUTABLE"},
    )


def test_ecr_repository_keeps_only_last_thirty_images(template):
    template.has_resource_properties(
        "AWS::ECR::Repository",
        {
            "LifecyclePolicy": {
                "LifecyclePolicyText": Match.string_like_regexp(r'"countNumber":30'),
            }
        },
    )


def test_ecr_repository_uri_and_name_are_exported(template):
    (repository_id,) = template.find_resources("AWS::ECR::Repository").keys()

    template.has_output(
        "*",
        {"Value": {"Fn::Join": Match.array_with([Match.array_with([{"Ref": repository_id}])])}},
    )
    template.has_output("*", {"Value": {"Ref": repository_id}})
