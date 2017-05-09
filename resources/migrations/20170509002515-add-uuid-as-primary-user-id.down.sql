ALTER TABLE users ADD COLUMN id SERIAL NOT NULL;

ALTER TABLE users 
ADD UNIQUE(id);

ALTER TABLE categories
ADD COLUMN user_id int NOT NULL;

ALTER TABLE tasks
ADD COLUMN user_id int NOT NULL;

ALTER TABLE "categories" ADD CONSTRAINT "fk_categories_user_id" FOREIGN KEY("user_id")
REFERENCES "users" ("id");

ALTER TABLE "tasks" ADD CONSTRAINT "fk_tasks_user_id" FOREIGN KEY("user_id")
REFERENCES "users" ("id");

ALTER TABLE users DROP CONSTRAINT users_pkey CASCADE;

ALTER TABLE users
DROP COLUMN uid CASCADE;

ALTER TABLE categories
DROP COLUMN user_uid;

ALTER TABLE tasks
DROP COLUMN user_uid;

ALTER TABLE users ADD CONSTRAINT "pk_users" PRIMARY KEY ("id");
