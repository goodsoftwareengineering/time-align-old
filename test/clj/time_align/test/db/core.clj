(ns time-align.test.db.core
  (:require [time-align.db.core :refer [*db*] :as db]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [time-align.config :refer [env]]
            [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'time-align.config/env
      #'time-align.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-analytics
  (jdbc/with-db-transaction
    [t-conn *db*]
    (is (= 1
           (db/create-analytic! t-conn {:dispatch_key ":test"
                                        :payload {:chump "tastic"}})
           (count (db/get-analytics t-conn {}))))
    ))
