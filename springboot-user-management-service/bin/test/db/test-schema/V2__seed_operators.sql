-- The same three operators the development stack seeds, so a failure here means
-- what it would mean in the stack. Hashes are bcrypt cost 12.
INSERT INTO auth_user (username, password_hash, roles, enabled) VALUES
  ('admin',    '$2a$12$l.1bltjREuZecySd7BaeturH.54X.DqpILM7Qy9n/N1GI8heqMXFy', 'ROLE_ADMIN',  TRUE),
  ('viewer',   '$2a$12$8p9ReBN01MBjyZ58NTqrvuxUeZZ6hIl6IQdEvt2puJrSq4yBZbGQS', 'ROLE_VIEWER', TRUE),
  ('noaccess', '$2a$12$pkCdDdaKZojDaoGgxZ4IweNM75N5fP47Yn5seAMuQ7YijNDoLyJ7e', 'ROLE_NONE',   TRUE),
  ('disabled', '$2a$12$l.1bltjREuZecySd7BaeturH.54X.DqpILM7Qy9n/N1GI8heqMXFy', 'ROLE_ADMIN',  FALSE);
