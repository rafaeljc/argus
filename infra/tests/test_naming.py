from argus.naming import BACKEND_REPOSITORY_NAME, Naming

PROD = Naming(environment="prod")


def test_resource_name_carries_the_environment() -> None:
    assert PROD.resource("foundation") == "argus-prod-foundation"


def test_resource_name_joins_multi_word_components_with_hyphens() -> None:
    assert PROD.resource("cd", "backend") == "argus-prod-cd-backend"


def test_a_second_environment_never_collides_with_prod() -> None:
    assert Naming(environment="staging").resource("db") == "argus-staging-db"


def test_input_parameter_path_is_environment_scoped() -> None:
    assert PROD.input_parameter("domain-name") == "/argus/prod/domain-name"


def test_input_parameter_path_supports_the_env_subtree() -> None:
    assert PROD.input_parameter("env", "app-base-url") == "/argus/prod/env/app-base-url"


def test_output_parameter_path_is_keyed_by_component() -> None:
    assert PROD.output_parameter("backend", "cluster-name") == (
        "/argus/prod/out/backend/cluster-name"
    )


def test_outputs_live_under_a_prefix_that_inputs_never_use() -> None:
    assert PROD.output_parameter("frontend", "bucket-name").startswith("/argus/prod/out/")


def test_frontend_bucket_name_is_qualified_by_account_to_stay_globally_unique() -> None:
    assert PROD.frontend_bucket("123456789012") == "argus-prod-frontend-123456789012"


def test_the_backend_repository_is_the_one_environment_agnostic_name() -> None:
    assert BACKEND_REPOSITORY_NAME == "argus-backend"
