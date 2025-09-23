CREATE SCHEMA verify;

CREATE TABLE IF NOT EXISTS verify.authorization_request_details (
    request_id character varying(40) NOT NULL,
    transaction_id character varying(40) NOT NULL,
    authorization_details text NOT NULL,
    expires_at bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS verify.presentation_definition(
    id character varying(36) NOT NULL,
    input_descriptors jsonb NOT NULL,
    name character varying(500),
    purpose character varying(500),
    vp_format text,
    submission_requirements text
);

CREATE TABLE IF NOT EXISTS verify.vc_submission(
    transaction_id character varying(40) NOT NULL,
    vc text NOT NULL
);

CREATE TABLE IF NOT EXISTS verify.vp_submission(
    request_id character varying(40) NOT NULL,
    vp_token VARCHAR NOT NULL,
    presentation_submission text NOT NULL
);

CREATE TABLE verify.bank_credentials (
    bank_id VARCHAR(100) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    bank_secret VARCHAR(255) NOT NULL,
    bank_webhook_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT bank_credentials_pkey PRIMARY KEY (bank_id)
);

CREATE TABLE vp_requests (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    bank_credential_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_bank_credential
        FOREIGN KEY (bank_credential_id)
        REFERENCES bank_credentials (bank_id)
        ON DELETE CASCADE
);