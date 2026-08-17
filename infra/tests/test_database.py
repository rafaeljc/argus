def test_database_is_not_publicly_accessible(template):
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {"PubliclyAccessible": False},
    )


def test_database_uses_postgres_engine(template):
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {"Engine": "postgres", "DBName": "argus"},
    )


def test_database_credentials_are_a_generated_secret(template):
    # from_generated_secret attaches the secret to the instance via a
    # SecretTargetAttachment — no human ever sees or types the password.
    template.resource_count_is("AWS::SecretsManager::Secret", 1)
    template.resource_count_is("AWS::SecretsManager::SecretTargetAttachment", 1)


def test_database_keeps_seven_day_backups(template):
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {"BackupRetentionPeriod": 7},
    )


def test_database_is_encrypted_at_rest(template):
    template.has_resource_properties(
        "AWS::RDS::DBInstance",
        {"StorageEncrypted": True},
    )
