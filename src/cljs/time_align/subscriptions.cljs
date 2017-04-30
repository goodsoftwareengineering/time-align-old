(ns time-align.subscriptions
  (:require [re-frame.core :refer [reg-sub]]

            [cljs.pprint :refer [pprint]]
            ))

(reg-sub
  :page
  (fn [db _]
    (get-in db [:view :page])))

(reg-sub
 :queue
 (fn [db _]
   (->> db
        (:tasks)
        (filter #(= 0 (count (:periods %)))))))

(reg-sub
 :planned
 (fn [db _]
   (->> db
        (:tasks)
        (filter #(:planned %)))))

(reg-sub
 :actual
 (fn [db _]
   (->> db
        (:tasks)
        (filter #(not (:planned %))))))

;; TODO remove this
(reg-sub
 :tasks
 (fn [db _]
   (:tasks db)))

