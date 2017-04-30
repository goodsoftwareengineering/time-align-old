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

(defn date-string
  "creates a string in yyyy-mm-dd format from a js date obj"
  [date]
  (str (.getFullYear date) "-"
       (+ 1 (.getMonth date)) "-"
       (.getDate date)))

(defn zero-in-day
  "taking a date obj, or string, will return a new date object with Hours, Minutes, Seconds, and Milliseconds set to default 0"
  [date]
  (let [d (if (string? date)
            (clojure.string/replace date #"-" "/") ;; sql needs "-" but js/Date does wierd time zone stuff unless the string uses "/"
            date)]
    (new js/Date (date-string (new js/Date d)))))

(def day-ms
  (->> 1
       (* 24)
       (* 60)
       (* 60)
       (* 1000)))

(reg-sub
 :visible-days
 (fn [db _]
   (as-> (->> db
              (:tasks)
              (map #(:periods %))
              (flatten)
              (filter #(not (nil? %)))
              (map #(vec %))
              (flatten)
              (filter inst?)
              (map #(.valueOf (zero-in-day %)))
              (sort))
       dates
     (range (first dates) (last dates) day-ms)
     (map #(new js/Date %) dates))))

