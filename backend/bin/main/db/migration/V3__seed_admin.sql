-- The seeded operator. The hash comes from configuration
-- (SEED_ADMIN_PASSWORD_HASH), never from this file, so no environment ships
-- with a password that is readable in version control.
INSERT INTO auth_user (username, password_hash, roles, enabled)
VALUES ('${seedAdminUsername}', '${seedAdminPasswordHash}', 'ROLE_ADMIN', TRUE)
ON CONFLICT DO NOTHING;
