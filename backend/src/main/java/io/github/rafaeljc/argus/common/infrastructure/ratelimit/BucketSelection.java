package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

public record BucketSelection(String bucketName, String key) {

    public BucketSelection {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("bucketName must not be blank");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
