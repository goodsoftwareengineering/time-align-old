CREATE TABLE "users" (
    "id" int  NOT NULL ,
    "name" varchar(255)  NOT NULL ,
    "email" varchar(255)  NOT NULL ,
    "hash_pass" varchar(255)  NOT NULL ,
    CONSTRAINT "pk_users" PRIMARY KEY (
        "id"
    )
);

CREATE TABLE "categories" (
    "id" int  NOT NULL ,
    "user_id" int  NOT NULL ,
    "name" varchar(255)  NOT NULL ,
    "color" varchar(6)  NOT NULL ,
    "info" jsonb  NOT NULL ,
    CONSTRAINT "pk_categories" PRIMARY KEY (
        "id"
    )
);

CREATE TABLE "tasks" (
    "id" int  NOT NULL ,
    "user_id" int  NOT NULL ,
    "category_id" int  NOT NULL ,
    "priority" int  NOT NULL ,
    CONSTRAINT "pk_tasks" PRIMARY KEY (
        "id"
    )
);

CREATE TABLE "periods" (
    "id" int  NOT NULL ,
    "task_id" int  NOT NULL ,
    "time_range" tstzrange  NOT NULL ,
    CONSTRAINT "pk_periods" PRIMARY KEY (
        "id"
    )
);

CREATE TABLE "dependencies" (
    "id" int  NOT NULL ,
    "task_id" int  NOT NULL ,
    "dependency_id" int  NOT NULL ,
    CONSTRAINT "pk_dependencies" PRIMARY KEY (
        "id"
    )
);

ALTER TABLE "categories" ADD CONSTRAINT "fk_categories_user_id" FOREIGN KEY("user_id")
REFERENCES "users" ("id");

ALTER TABLE "tasks" ADD CONSTRAINT "fk_tasks_user_id" FOREIGN KEY("user_id")
REFERENCES "users" ("id");

ALTER TABLE "tasks" ADD CONSTRAINT "fk_tasks_category_id" FOREIGN KEY("category_id")
REFERENCES "categories" ("id");

ALTER TABLE "periods" ADD CONSTRAINT "fk_periods_task_id" FOREIGN KEY("task_id")
REFERENCES "tasks" ("id");

ALTER TABLE "dependencies" ADD CONSTRAINT "fk_dependencies_task_id" FOREIGN KEY("task_id")
REFERENCES "tasks" ("id");

ALTER TABLE "dependencies" ADD CONSTRAINT "fk_dependencies_dependency_id" FOREIGN KEY("dependency_id")
REFERENCES "tasks" ("id");
