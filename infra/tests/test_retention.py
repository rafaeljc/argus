from aws_cdk import RemovalPolicy

from argus.retention import Durability


def test_retained_resources_survive_stack_deletion() -> None:
    assert Durability.RETAINED.removal_policy is RemovalPolicy.RETAIN


def test_disposable_resources_are_deleted_with_their_stack() -> None:
    assert Durability.DISPOSABLE.removal_policy is RemovalPolicy.DESTROY


def test_every_durability_level_is_explicit_about_its_policy() -> None:
    assert {level.removal_policy for level in Durability} == {
        RemovalPolicy.RETAIN,
        RemovalPolicy.DESTROY,
    }
