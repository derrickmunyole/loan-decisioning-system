CREATE TABLE policy_version (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    rules_json JSONB NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE TABLE scorecard_version (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    formula_config_json JSONB NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE TABLE pricing_version (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    apr_term_rules_json JSONB NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
