-- Domain user records (functional spec FR-1).
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

-- BR-1 lives here, not in the service layer: this is what makes email
-- uniqueness true under concurrent writes.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

-- Default sort order for the list endpoint (FR-5).
CREATE INDEX ix_users_created_at ON users (created_at DESC);
