package io.github.derrickmunyole.loandecisioning.origination.document;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.Audited;
import io.github.derrickmunyole.loandecisioning.origination.application.Application;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationNotEditableException;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationNotFoundException;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Documents can only be uploaded while the application is still DRAFT (see docs/adr for why). */
@Service
public class DocumentService {

    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;

    public DocumentService(
            ApplicationRepository applicationRepository,
            DocumentRepository documentRepository,
            DocumentStorageService documentStorageService) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
    }

    @Transactional
    @Audited(action = "DOCUMENT_UPLOADED", targetType = "Document", targetId = "#documentId")
    public DocumentResponse upload(
            UUID applicationId, UUID documentId, DocumentType documentType, MultipartFile file) {
        Application application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        if (!application.isDraft()) {
            throw new ApplicationNotEditableException(applicationId);
        }

        byte[] content = readBytes(file);
        String checksum = sha256Hex(content);
        String storageKey = "applications/%s/%s".formatted(applicationId, documentId);
        documentStorageService.put(storageKey, content, file.getContentType());

        Document document =
                documentRepository.save(
                        new Document(
                                documentId,
                                applicationId,
                                documentType,
                                storageKey,
                                file.getOriginalFilename(),
                                file.getContentType(),
                                content.length,
                                checksum));
        return DocumentResponse.from(document);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
