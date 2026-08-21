"""The five stacks, and the order they have to exist in.

Split by what has to be deployed together, not by what happens to be related:

* ``foundation`` depends on nothing, so it can be deployed into an empty
  account. That is what breaks the chicken-and-egg -- the service needs an
  image, the image needs a repository, and the repository lives here.
* the rest need a VPC, a database and an image, so they go in the second pass.

CloudFormation works the order out for itself from the references between them.
"""

from dataclasses import dataclass

from aws_cdk import Tags
from constructs import Construct

from argus.config import EnvironmentConfig
from argus.stacks.compute import ComputeStack
from argus.stacks.data import DataStack
from argus.stacks.edge import EdgeStack
from argus.stacks.foundation import FoundationStack
from argus.stacks.network import NetworkStack


@dataclass(frozen=True)
class ArgusEnvironment:
    """Every stack making up one environment."""

    foundation: FoundationStack
    network: NetworkStack
    data: DataStack
    compute: ComputeStack
    edge: EdgeStack


def build_environment(scope: Construct, config: EnvironmentConfig) -> ArgusEnvironment:
    """Create the whole environment under ``scope``."""
    naming = config.naming

    foundation = FoundationStack(scope, naming.resource("foundation"), config=config)
    network = NetworkStack(scope, naming.resource("network"), config=config)
    data = DataStack(scope, naming.resource("data"), config=config, vpc=network.vpc)
    compute = ComputeStack(
        scope,
        naming.resource("compute"),
        config=config,
        vpc=network.vpc,
        database=data.database,
        connection_secret=data.connection_secret,
    )
    edge = EdgeStack(
        scope,
        naming.resource("edge"),
        config=config,
        load_balancer=compute.backend.load_balancer,
    )

    # Applied to everything, so the orphan audit can ask the account what it
    # believes belongs to this project and compare it against what the stacks
    # actually contain.
    Tags.of(scope).add("argus:environment", config.name)
    Tags.of(scope).add("argus:managed-by", "cdk")

    return ArgusEnvironment(
        foundation=foundation, network=network, data=data, compute=compute, edge=edge
    )
