from aws_cdk import aws_ecs as ecs
from aws_cdk import aws_ssm as ssm
from constructs import Construct


class Parameters:
    def __init__(self, env_name: str) -> None:
        self._env_name = env_name

    def name(self, param: str) -> str:
        return f"/argus/{self._env_name}/{param}"

    def synth_time_value(self, scope: Construct, param: str) -> str:
        return ssm.StringParameter.value_from_lookup(scope, self.name(param))

    def deploy_time_value(self, scope: Construct, param: str) -> str:
        return ssm.StringParameter.value_for_string_parameter(scope, self.name(param))

    def ssm_secret(self, scope: Construct, construct_id: str, param: str) -> ecs.Secret:
        string_param = ssm.StringParameter.from_string_parameter_name(
            scope, construct_id, self.name(param)
        )
        return ecs.Secret.from_ssm_parameter(string_param)
