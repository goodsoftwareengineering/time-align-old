CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users
  ADD COLUMN uid UUID NOT NULL
  CONSTRAINT gen_uuid_for_id DEFAULT gen_random_uuid();

ALTER TABLE users
  ADD UNIQUE (uid);

ALTER TABLE categories
  ADD COLUMN user_uid UUID NOT NULL;

ALTER TABLE tasks
  ADD COLUMN user_uid UUID NOT NULL;

ALTER TABLE "categories"
  ADD CONSTRAINT "fk_categories_user_uid" FOREIGN KEY ("user_uid")
REFERENCES "users" ("uid");

ALTER TABLE "tasks"
  ADD CONSTRAINT "fk_tasks_user_uid" FOREIGN KEY ("user_uid")
REFERENCES "users" ("uid");

ALTER TABLE users
  DROP CONSTRAINT pk_users CASCADE;

ALTER TABLE users
  DROP COLUMN id CASCADE;

ALTER TABLE categories
  DROP COLUMN user_id;

ALTER TABLE tasks
  DROP COLUMN user_id;

ALTER TABLE users
  ADD PRIMARY KEY (uid);
