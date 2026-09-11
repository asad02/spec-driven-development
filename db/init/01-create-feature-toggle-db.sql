-- Runs only on a fresh volume, via the postgres image's entrypoint. The image
-- creates POSTGRES_DB (usersdb) itself; the feature service owns a second,
-- separate database, so it is created here.
CREATE DATABASE "feature-toggle";
