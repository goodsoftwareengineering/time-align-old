-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(id, first_name, last_name, email, pass)
VALUES (:id, :first_name, :last_name, :email, :pass)

-- :name update-user! :! :n
-- :doc update an existing user record
UPDATE users
SET first_name = :first_name, last_name = :last_name, email = :email
WHERE id = :id

-- :name get-user :? :1
-- :doc retrieve a user given the id.
SELECT *
FROM users
WHERE id = :id

-- :name delete-user! :! :n
-- :doc delete a user given the id
DELETE FROM users
WHERE id = :id

-- :name create-analytic! :! :n
-- :doc creates an analytic
INSERT INTO analytics
(dispatch_key, payload)
VALUES (:dispatch_key, :payload);

-- :name get-analytic-by-id :? :1
-- :doc retrieve a row in the analytic by id
SELECT * FROM analytics
WHERE id = :id;

-- :name get-analytics :? :*
-- :doc retrieve all analytics in table
SELECT * FROM analytics;
