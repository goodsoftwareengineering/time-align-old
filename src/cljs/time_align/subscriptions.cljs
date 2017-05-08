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
        (filter #(= 0 (count (:periods %)))))))

;; TODO remove this
(reg-sub
 :tasks
 (fn [db _]
   (:tasks db)))

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

