-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(name, email, hash_pass)
VALUES (:name, :email, :hash_pass);

-- :name update-user! :! :n
-- :doc update an existing user record
UPDATE users
SET name = :name, email = :email
WHERE uid = :uid;

-- :name get-user-by-id :? :1
-- :doc retrieve a user given the id.
SELECT *
FROM users
WHERE uid = :uid;

-- :name get-user-by-name :? :1
-- :doc retrieve a user given the name.
SELECT *
FROM users
WHERE name LIKE :name_like;

-- :name delete-user! :! :n
-- :doc delete a user given the id
DELETE FROM users
WHERE uid = :uid;

-- :name create-analytic! :! :n
-- :doc creates an analytic
INSERT INTO analytics
(dispatch_key, payload, ip_addr)
VALUES (:dispatch_key, :payload, :ip_addr);

-- :name get-analytic-by-id :? :1
-- :doc retrieve a row in the analytic by id
SELECT * FROM analytics
WHERE id = :id;

-- :name get-analytics :? :*
-- :doc retrieve all analytics in table
SELECT * FROM analytics;
