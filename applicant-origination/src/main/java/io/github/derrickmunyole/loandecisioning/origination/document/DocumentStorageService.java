package io.github.derrickmunyole.loandecisioning.origination.document;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Documents never touch Postgres — only object key, checksum, and metadata do. */
@Service
class DocumentStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    DocumentStorageService(
            MinioClient minioClient, @Value("${app.storage.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    void put(String storageKey, byte[] content, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            throw new DocumentStorageException("Failed to store document at " + storageKey, e);
        }
    }
}
