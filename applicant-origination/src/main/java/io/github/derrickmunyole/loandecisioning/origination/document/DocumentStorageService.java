package io.github.derrickmunyole.loandecisioning.origination.document;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Documents never touch Postgres — only object key, checksum, and metadata do. */
@Service
class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

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

    /**
     * Best-effort compensation for a MinIO write whose corresponding Document row never made it
     * into Postgres. Deliberately swallows failures here — a cleanup that itself fails should
     * never mask the original error that triggered it.
     */
    void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
        } catch (Exception e) {
            log.warn("Failed to clean up orphaned document object {}", storageKey, e);
        }
    }
}
