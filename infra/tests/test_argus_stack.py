import pytest
from aws_cdk import App, Environment

from argus_infra.argus_stack import ArgusStack
from argus_infra.config import ENVIRONMENTS

TEST_AWS_ENV = Environment(account="123456789012", region="us-east-1")


def test_synth_without_image_tag_raises():
    app = App()

    with pytest.raises(ValueError, match="image_tag"):
        ArgusStack(app, "Test", env_config=ENVIRONMENTS["prod"], env=TEST_AWS_ENV)


def test_synth_with_image_tag_succeeds():
    app = App(context={"image_tag": "test-tag"})

    stack = ArgusStack(app, "Test", env_config=ENVIRONMENTS["prod"], env=TEST_AWS_ENV)

    assert stack is not None


def test_certificate_covers_apex_and_api_hostname(template):
    template.has_resource_properties(
        "AWS::CertificateManager::Certificate",
        {"ValidationMethod": "DNS"},
    )
    certs = template.find_resources("AWS::CertificateManager::Certificate")
    assert len(certs) == 1
    (cert,) = certs.values()
    assert len(cert["Properties"]["SubjectAlternativeNames"]) == 2
