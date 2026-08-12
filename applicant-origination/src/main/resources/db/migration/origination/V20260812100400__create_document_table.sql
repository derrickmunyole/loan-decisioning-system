CREATE TABLE document (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application (id),
    application_version_id UUID REFERENCES application_version (id),
    document_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(300) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_document_application_id ON document (application_id);
CREATE INDEX idx_document_application_version_id ON document (application_version_id);
