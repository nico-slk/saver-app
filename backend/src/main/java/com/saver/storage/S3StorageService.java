package com.saver.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Adaptador para AWS S3 y proveedores compatibles con la API S3. */
public class S3StorageService implements StorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final long maxFileSizeBytes;

    public S3StorageService(S3Client client, S3Presigner presigner, String bucket, long maxFileSizeBytes) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public StoredObject store(StorageUpload upload) {
        validateUpload(upload);
        String key = UUID.randomUUID().toString();
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                            .contentType(upload.contentType()).contentLength(upload.contentLength()).build(),
                    RequestBody.fromInputStream(upload.content(), upload.contentLength()));
            return new StoredObject(key, upload.contentLength(), upload.contentType());
        } catch (S3Exception e) {
            throw new StorageException("Could not store object", e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            ResponseInputStream<?> response = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key(storageKey)).build());
            return response;
        } catch (S3Exception e) {
            throw new StorageException("Could not read object", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key(storageKey)).build());
        } catch (S3Exception e) {
            throw new StorageException("Could not delete object", e);
        }
    }

    @Override
    public Optional<StorageObjectMetadata> metadata(String storageKey) {
        try {
            var response = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key(storageKey)).build());
            return Optional.of(new StorageObjectMetadata(storageKey, response.contentLength(), response.contentType(), response.lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw new StorageException("Could not read object metadata", e);
        }
    }

    @Override
    public URI signedDownloadUrl(String storageKey, Duration validity) {
        if (validity == null || validity.isZero() || validity.isNegative() || validity.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Signed URL validity must be between 1 second and 1 hour");
        }
        return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(validity)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key(storageKey)).build()).build()).url().toString());
    }

    /** Crea el bucket si no existe. Pensado para el entorno local (SeaweedFS). */
    @Override
    public void ensureReady() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) throw new StorageException("Could not check bucket", e);
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private void validateUpload(StorageUpload upload) {
        if (upload == null || upload.content() == null || upload.contentLength() < 0 || upload.contentLength() > maxFileSizeBytes) {
            throw new IllegalArgumentException("Invalid upload size");
        }
        if (upload.contentType() == null || upload.contentType().isBlank() || upload.contentType().length() > 255) {
            throw new IllegalArgumentException("Invalid content type");
        }
    }

    private static String key(String storageKey) {
        try {
            UUID id = UUID.fromString(storageKey);
            if (!id.toString().equals(storageKey)) throw new IllegalArgumentException("Invalid storage key");
            return storageKey;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid storage key", e);
        }
    }
}
