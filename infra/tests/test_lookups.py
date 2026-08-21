import pytest

from argus.lookups import CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST, MissingInputsError, resolve_inputs
from argus.naming import Naming

PROD = Naming(environment="prod")

COMPLETE_PARAMETERS = {
    "/argus/prod/domain-name": "argusapp.click",
    "/argus/prod/hosted-zone-id": "Z0123456789ABCDEFGHIJ",
    "/argus/prod/image-tag": "1f2e3d4c5b6a798877665544332211ffeeddccbb",
    "/argus/prod/alert-email": "ops@argusapp.click",
}


class FakeLookups:
    """In-memory stand-in for the AWS boundary, so tests never touch AWS."""

    def __init__(
        self,
        parameters: dict[str, str] | None = None,
        prefix_lists: dict[str, str] | None = None,
    ) -> None:
        self.parameters = dict(COMPLETE_PARAMETERS if parameters is None else parameters)
        self.prefix_lists = (
            {CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST: "pl-3b927c52"}
            if prefix_lists is None
            else dict(prefix_lists)
        )

    def ssm_parameter(self, name: str) -> str | None:
        return self.parameters.get(name)

    def managed_prefix_list_id(self, name: str) -> str | None:
        return self.prefix_lists.get(name)


def test_resolves_every_input_when_all_are_present() -> None:
    inputs = resolve_inputs(FakeLookups(), PROD)

    assert inputs.domain_name == "argusapp.click"
    assert inputs.hosted_zone_id == "Z0123456789ABCDEFGHIJ"
    assert inputs.image_tag == "1f2e3d4c5b6a798877665544332211ffeeddccbb"
    assert inputs.alert_email == "ops@argusapp.click"
    assert inputs.cloudfront_prefix_list_id == "pl-3b927c52"


def test_missing_parameter_names_the_exact_path() -> None:
    parameters = {k: v for k, v in COMPLETE_PARAMETERS.items() if k != "/argus/prod/image-tag"}

    with pytest.raises(MissingInputsError) as raised:
        resolve_inputs(FakeLookups(parameters=parameters), PROD)

    assert "/argus/prod/image-tag" in str(raised.value)


def test_missing_parameter_spells_out_the_command_that_fixes_it() -> None:
    parameters = {k: v for k, v in COMPLETE_PARAMETERS.items() if k != "/argus/prod/domain-name"}

    with pytest.raises(MissingInputsError) as raised:
        resolve_inputs(FakeLookups(parameters=parameters), PROD)

    assert "aws ssm put-parameter --name /argus/prod/domain-name --type String --value" in str(
        raised.value
    )


def test_all_missing_inputs_are_reported_by_a_single_failure() -> None:
    with pytest.raises(MissingInputsError) as raised:
        resolve_inputs(FakeLookups(parameters={}, prefix_lists={}), PROD)

    message = str(raised.value)
    for path in COMPLETE_PARAMETERS:
        assert path in message
    assert CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST in message


def test_a_blank_parameter_value_counts_as_missing() -> None:
    with pytest.raises(MissingInputsError) as raised:
        resolve_inputs(
            FakeLookups(parameters={**COMPLETE_PARAMETERS, "/argus/prod/domain-name": "  "}), PROD
        )

    assert "/argus/prod/domain-name" in str(raised.value)


def test_an_unresolvable_prefix_list_reports_its_own_remediation() -> None:
    with pytest.raises(MissingInputsError) as raised:
        resolve_inputs(FakeLookups(prefix_lists={}), PROD)

    message = str(raised.value)
    assert CLOUDFRONT_ORIGIN_FACING_PREFIX_LIST in message
    assert "put-parameter" not in message
