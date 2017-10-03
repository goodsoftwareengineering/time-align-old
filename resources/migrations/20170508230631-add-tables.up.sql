CREATE TABLE "users" (
  "id"        INT          NOT NULL,
  "name"      VARCHAR(255) NOT NULL,
  "email"     VARCHAR(255) NOT NULL,
  "hash_pass" VARCHAR(255) NOT NULL,
  CONSTRAINT "pk_users" PRIMARY KEY (
    "id"
  )
);

CREATE TABLE "categories" (
  "id"      INT          NOT NULL,
  "user_id" INT          NOT NULL,
  "name"    VARCHAR(255) NOT NULL,
  "color"   VARCHAR(6)   NOT NULL,
  "info"    JSONB        NOT NULL,
  CONSTRAINT "pk_categories" PRIMARY KEY (
    "id"
  )
);

CREATE TABLE "tasks" (
  "id"          INT NOT NULL,
  "user_id"     INT NOT NULL,
  "category_id" INT NOT NULL,
  "priority"    INT NOT NULL,
  CONSTRAINT "pk_tasks" PRIMARY KEY (
    "id"
  )
);

CREATE TABLE "periods" (
  "id"         INT       NOT NULL,
  "task_id"    INT       NOT NULL,
  "time_range" TSTZRANGE NOT NULL,
  CONSTRAINT "pk_periods" PRIMARY KEY (
    "id"
  )
);

CREATE TABLE "dependencies" (
  "id"            INT NOT NULL,
  "task_id"       INT NOT NULL,
  "dependency_id" INT NOT NULL,
  CONSTRAINT "pk_dependencies" PRIMARY KEY (
    "id"
  )
);

ALTER TABLE "categories"
  ADD CONSTRAINT "fk_categories_user_id" FOREIGN KEY ("user_id")
REFERENCES "users" ("id");

ALTER TABLE "tasks"
  ADD CONSTRAINT "fk_tasks_user_id" FOREIGN KEY ("user_id")
REFERENCES "users" ("id");

ALTER TABLE "tasks"
  ADD CONSTRAINT "fk_tasks_category_id" FOREIGN KEY ("category_id")
REFERENCES "categories" ("id");

ALTER TABLE "periods"
  ADD CONSTRAINT "fk_periods_task_id" FOREIGN KEY ("task_id")
REFERENCES "tasks" ("id");

ALTER TABLE "dependencies"
  ADD CONSTRAINT "fk_dependencies_task_id" FOREIGN KEY ("task_id")
REFERENCES "tasks" ("id");

ALTER TABLE "dependencies"
  ADD CONSTRAINT "fk_dependencies_dependency_id" FOREIGN KEY ("dependency_id")
REFERENCES "tasks" ("id");
