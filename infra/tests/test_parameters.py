from argus_infra.parameters import Parameters


def test_param_name_is_scoped_under_argus_env():
    params = Parameters("prod")

    assert params.name("app-base-url") == "/argus/prod/app-base-url"


def test_param_name_uses_the_environment_it_was_built_for():
    params = Parameters("staging")

    assert params.name("app-base-url") == "/argus/staging/app-base-url"
