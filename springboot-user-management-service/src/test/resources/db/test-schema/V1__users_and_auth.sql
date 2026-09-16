-- Test-only. In production the Micronaut service owns these tables and this
-- service runs ddl-auto=validate against them (technical spec §5.3). A test
-- database has no Micronaut to create them, so the schema is reproduced here.
CREATE TABLE users (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    phone       VARCHAR(20),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
CREATE INDEX ix_users_created_at ON users (created_at DESC);

CREATE TABLE auth_user (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    roles         VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_auth_user PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ux_auth_user_username_lower ON auth_user (LOWER(username));
