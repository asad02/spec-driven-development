-- Operator logins. Deliberately no foreign key to users: an operator is not a
-- user record, and coupling them would force one to exist for the other.
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
