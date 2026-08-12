package io.github.derrickmunyole.loandecisioning.origination.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * {@code applicationVersionId} is null while the application is still DRAFT (documents can be
 * uploaded before a version exists) and gets stamped with the newly created version at submit —
 * see docs/blueprint.md's data model, which ties document to application_version. {@code id} is
 * caller-assigned (see {@link io.github.derrickmunyole.loandecisioning.origination.application.Application}
 * for why) since it doubles as the MinIO object key suffix and the {@code @Audited} target.
 */
@Entity
@Getter
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_version_id")
    private UUID applicationVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Document() {}

    public Document(
            UUID id,
            UUID applicationId,
            DocumentType documentType,
            String storageKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String checksum) {
        this.id = id;
        this.applicationId = applicationId;
        this.documentType = documentType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = Instant.now();
    }

    public void attachToVersion(UUID applicationVersionId) {
        this.applicationVersionId = applicationVersionId;
    }
}
