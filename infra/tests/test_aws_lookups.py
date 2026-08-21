from collections.abc import Iterator

import boto3
import pytest
from botocore.stub import Stubber
from mypy_boto3_ec2.client import EC2Client
from mypy_boto3_ssm.client import SSMClient

from argus.lookups import CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST, AwsLookups

REGION = "us-east-1"

StubbedSsm = tuple[SSMClient, Stubber]
StubbedEc2 = tuple[EC2Client, Stubber]


@pytest.fixture
def ssm() -> Iterator[StubbedSsm]:
    client = boto3.client("ssm", region_name=REGION)
    with Stubber(client) as stubber:
        yield client, stubber


@pytest.fixture
def ec2() -> Iterator[StubbedEc2]:
    client = boto3.client("ec2", region_name=REGION)
    with Stubber(client) as stubber:
        yield client, stubber


def test_reads_a_parameter_value(ssm: StubbedSsm) -> None:
    client, stubber = ssm
    stubber.add_response(
        "get_parameter",
        {"Parameter": {"Name": "/argus/prod/domain-name", "Value": "argusapp.click"}},
    )

    assert AwsLookups(ssm=client).ssm_parameter("/argus/prod/domain-name") == "argusapp.click"


def test_an_absent_parameter_reads_as_missing_rather_than_raising(
    ssm: StubbedSsm,
) -> None:
    client, stubber = ssm
    stubber.add_client_error("get_parameter", service_error_code="ParameterNotFound")

    assert AwsLookups(ssm=client).ssm_parameter("/argus/prod/nope") is None


def test_an_unexpected_ssm_error_is_not_swallowed(ssm: StubbedSsm) -> None:
    client, stubber = ssm
    stubber.add_client_error("get_parameter", service_error_code="AccessDeniedException")

    with pytest.raises(Exception, match="AccessDenied"):
        AwsLookups(ssm=client).ssm_parameter("/argus/prod/domain-name")


def test_resolves_a_managed_prefix_list_by_name(ec2: StubbedEc2) -> None:
    client, stubber = ec2
    stubber.add_response(
        "describe_managed_prefix_lists",
        {"PrefixLists": [{"PrefixListId": "pl-3b927c52"}]},
        {
            "Filters": [
                {"Name": "prefix-list-name", "Values": [CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST]}
            ]
        },
    )

    resolved = AwsLookups(ec2=client).managed_prefix_list_id(CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST)

    assert resolved == "pl-3b927c52"


def test_an_unknown_prefix_list_reads_as_missing(ec2: StubbedEc2) -> None:
    client, stubber = ec2
    stubber.add_response("describe_managed_prefix_lists", {"PrefixLists": []})

    assert AwsLookups(ec2=client).managed_prefix_list_id("com.amazonaws.nonexistent") is None
