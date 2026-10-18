CREATE TABLE app_user (
    id UUID NOT NULL PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    email_verified_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE verification_challenge (
    id UUID NOT NULL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX ix_verification_challenge_lookup ON verification_challenge (email, purpose, created_at DESC);
