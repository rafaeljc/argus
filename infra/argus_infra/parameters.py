class Parameters:
    def __init__(self, env_name: str) -> None:
        self._env_name = env_name

    def name(self, param: str) -> str:
        return f"/argus/{self._env_name}/{param}"
