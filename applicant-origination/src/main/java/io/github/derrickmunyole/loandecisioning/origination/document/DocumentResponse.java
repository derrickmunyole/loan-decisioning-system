package io.github.derrickmunyole.loandecisioning.origination.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        DocumentType documentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String checksum,
        DocumentStatus status,
        Instant createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getChecksum(),
                document.getStatus(),
                document.getCreatedAt());
    }
}
