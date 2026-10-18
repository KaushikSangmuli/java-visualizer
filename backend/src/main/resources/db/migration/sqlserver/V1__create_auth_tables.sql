CREATE TABLE app_user (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    email NVARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    email_verified_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE verification_challenge (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    email NVARCHAR(320) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    created_at DATETIME2 NOT NULL,
    expires_at DATETIME2 NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed_at DATETIME2 NULL
);

CREATE INDEX ix_verification_challenge_lookup
    ON verification_challenge (email, purpose, created_at DESC);
