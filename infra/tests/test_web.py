def test_bucket_blocks_all_public_access(template):
    template.has_resource_properties(
        "AWS::S3::Bucket",
        {
            "PublicAccessBlockConfiguration": {
                "BlockPublicAcls": True,
                "BlockPublicPolicy": True,
                "IgnorePublicAcls": True,
                "RestrictPublicBuckets": True,
            }
        },
    )


def test_distribution_has_exactly_one_origin_and_no_extra_behaviours(template):
    (distribution,) = template.find_resources("AWS::CloudFront::Distribution").values()
    config = distribution["Properties"]["DistributionConfig"]
    assert len(config["Origins"]) == 1
    # No API path gets a cache behaviour of its own — static content only.
    assert "CacheBehaviors" not in config or config["CacheBehaviors"] == []


def test_distribution_redirects_to_https(template):
    (distribution,) = template.find_resources("AWS::CloudFront::Distribution").values()
    default_behavior = distribution["Properties"]["DistributionConfig"]["DefaultCacheBehavior"]
    assert default_behavior["ViewerProtocolPolicy"] == "redirect-to-https"


def test_spa_routes_resolve_via_index_html_fallback(template):
    (distribution,) = template.find_resources("AWS::CloudFront::Distribution").values()
    errors = distribution["Properties"]["DistributionConfig"]["CustomErrorResponses"]
    by_code = {e["ErrorCode"]: e for e in errors}
    for code in (403, 404):
        assert by_code[code]["ResponseCode"] == 200
        assert by_code[code]["ResponsePagePath"] == "/index.html"


def test_distribution_serves_the_apex_domain(template):
    (distribution,) = template.find_resources("AWS::CloudFront::Distribution").values()
    aliases = distribution["Properties"]["DistributionConfig"]["Aliases"]
    assert "dummy-value-for-/argus/prod/domain-name" in aliases
