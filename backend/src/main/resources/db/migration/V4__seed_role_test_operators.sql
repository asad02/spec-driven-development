-- Two more operators so role-based access can actually be exercised end to end.
-- Hashes are bcrypt cost 12, generated for local development only:
--   viewer   / viewer123!
--   noaccess / noaccess123!
--
-- These are deliberately literal rather than placeholder-substituted: unlike the
-- admin account in V3 these are not real credentials for any environment, and a
-- deployment that does not want them can delete the rows.
INSERT INTO auth_user (username, password_hash, roles, enabled)
VALUES
  ('viewer',   '$2a$12$8p9ReBN01MBjyZ58NTqrvuxUeZZ6hIl6IQdEvt2puJrSq4yBZbGQS', 'ROLE_VIEWER', TRUE),
  ('noaccess', '$2a$12$pkCdDdaKZojDaoGgxZ4IweNM75N5fP47Yn5seAMuQ7YijNDoLyJ7e', 'ROLE_NONE',   TRUE)
ON CONFLICT DO NOTHING;
