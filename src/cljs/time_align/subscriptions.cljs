(ns time-align.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [time-align.utilities :as utils]

            [cljs.pprint :refer [pprint]]))

(reg-sub
  :page
  (fn [db _]
    (get-in db [:view :page])))

(reg-sub
 :drawer
 (fn [db _]
   (get-in db [:view :drawer])))

(reg-sub
 :queue
 (fn [db _]
   (->> db
        (:tasks)
        (filter :complete))))

;; TODO remove this
(reg-sub
 :tasks
 (fn [db _]
   (->> (:categories db)
        (map (fn [categories] (:tasks categories)))
        (flatten)
        (remove nil?)
        (remove empty?))))

(reg-sub
 :visible-days
 (fn [db _]
   (let [start-inst (get-in db [:view :range :start])
         start-ms   (.valueOf (utils/zero-in-day start-inst))
         stop-inst (get-in db [:view :range :stop])
         stop-ms   (.valueOf (utils/zero-in-day stop-inst))
         days-in-ms (range start-ms
                           stop-ms
                           utils/day-ms)]

     (if (= start-ms stop-ms)
       (list (new js/Date start-ms))
       (map #(new js/Date %) days-in-ms)))))

(reg-sub
 :selected-period
 (fn [db _]
   (get-in db [:view :selected :selected-period])))

(reg-sub
 :selected-task
 (fn [db _]
   (get-in db [:view :selected :selected-task])))
