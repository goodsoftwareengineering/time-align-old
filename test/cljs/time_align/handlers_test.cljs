(ns time-align.handlers-test
  (:require [clojure.test :refer [is are deftest testing use-fixtures]]
            [clojure.spec.alpha :as spec]
            [expound.alpha :as expound]
            [time-align.db :as db]
            [pjstadig.humane-test-output]
            [clojure.data :as d]
            [time-align.handlers :as rc]))

(def data-map {:save-period-form/no-task []
               :save-period-form/normal-result []})

(deftest initialize-db
  (testing "Initialize DB and worker"
    (let [{:keys [init-worker db] :as ret-val} (rc/initialize-db nil nil)]
      (is (some? db) ":db should be present")
      (is (some? init-worker) ":worker should be present")
      (is (= (type init-worker) (type "string"))
          "init worker should be declared with string"))))

;(deftest save-period-form
;  (testing "save-period-form with no task"
;    (let [cofx (:save-period-form/no-task data-map)]
;      (let [res-db (:db (rc/save-period-form cofx [_ _]))
;            old-db (:db cofx)
;            result (d/diff res-db old-db)]
;        (is (= [{:view {:period-form {:error-or-nil :no-task}}} nil nil] result)))))
;  (testing "save-period-form with normal result"))
