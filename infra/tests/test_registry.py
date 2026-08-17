from aws_cdk.assertions import Match


def test_ecr_repository_has_immutable_tags(template):
    template.has_resource_properties(
        "AWS::ECR::Repository",
        {"ImageTagMutability": "IMMUTABLE"},
    )


def test_ecr_repository_keeps_only_last_ten_images(template):
    template.has_resource_properties(
        "AWS::ECR::Repository",
        {
            "LifecyclePolicy": {
                "LifecyclePolicyText": Match.string_like_regexp(r'"countNumber":10'),
            }
        },
    )
