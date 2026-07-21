ALTER TABLE devices
    ADD COLUMN credential_hash VARCHAR(64),
    ADD COLUMN enrollment_code_hash VARCHAR(64),
    ADD COLUMN enrollment_expires_at TIMESTAMPTZ;
