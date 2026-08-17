def test_vpc_spans_two_availability_zones_with_three_subnet_tiers(template):
    # PUBLIC, PRIVATE_WITH_EGRESS, PRIVATE_ISOLATED x 2 AZs = 6 subnets
    template.resource_count_is("AWS::EC2::Subnet", 6)


def test_vpc_has_exactly_one_nat_gateway(template):
    # nat_gateways=1: the app calls Resend and the market-data vendor over the
    # open internet from the private-with-egress subnet, so NAT is required,
    # but a single instance doesn't need one per AZ.
    template.resource_count_is("AWS::EC2::NatGateway", 1)
