def test_trust_policy_sub_is_an_exact_repo_and_branch_match(template):
    # A wildcard here would let any branch, or any fork of the repo, assume the role.
    web_identity_statements = [
        statement
        for role in template.find_resources("AWS::IAM::Role").values()
        for statement in role["Properties"]["AssumeRolePolicyDocument"]["Statement"]
        if statement.get("Action") == "sts:AssumeRoleWithWebIdentity"
    ]
    assert len(web_identity_statements) == 2  # backend + frontend deploy roles

    for statement in web_identity_statements:
        sub = statement["Condition"]["StringEquals"]["token.actions.githubusercontent.com:sub"]
        assert sub == "repo:rafaeljc/argus:ref:refs/heads/main"


def test_no_policy_grants_cloudformation_actions_beyond_describe_stacks(template):
    cloudformation_actions = {
        action
        for policy in template.find_resources("AWS::IAM::Policy").values()
        for statement in policy["Properties"]["PolicyDocument"]["Statement"]
        for action in (
            statement["Action"] if isinstance(statement["Action"], list) else [statement["Action"]]
        )
        if action.startswith("cloudformation:")
    }
    assert cloudformation_actions == {"cloudformation:DescribeStacks"}
