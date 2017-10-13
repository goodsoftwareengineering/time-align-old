(ns time-align.utilities
  (:require [clojure.string :as string]
            #?(:cljs [cljsjs.moment-timezone])
            #?(:clj [java-time :as t])))

(defn make-date
  ([] #?(:cljs (js/moment.tz (js/Date.) "UTC")
         :clj  (t/with-zone (t/zoned-date-time) "UTC")))
  ( [year month day]
    (make-date year month day 0))
  ( [year month day hour]
    (make-date year month day hour 0))
  ( [year month day hour minute]
    (make-date year month day hour minute 0))
  ( [year month day hour minute second]
    (make-date year month day hour minute second 0))
  ( [year month day hour minute second millisecond]
    #?(:cljs (-> (js/Date. (.UTC js/Date year (- 1 month) day hour minute second millisecond))
                 (js/moment.tz "UTC"))
       ;;The api for zoned-date-time takes nanoseconds as its last variable
       :clj (t/with-zone
              (t/zoned-date-time year month day hour minute second (* 1000 millisecond))
              "UTC"))))

(defn get-default-timezone
  []
  #?(:cljs (.guess js/moment.tz)
     :clj  (t/zone-id)))

(def week-ms
  (->> 1
       (* 7)
       (* 24)
       (* 60)
       (* 60)
       (* 1000)))


(def day-ms
  (->> 1
       (* 24)
       (* 60)
       (* 60)
       (* 1000)))


(def hour-ms
  (->> 1
       (* 60)
       (* 60)
       (* 1000)))


(def ms-in-day
  (->> 1
       (* 24)
       (* 60)
       (* 60)
       (* 1000)))


(defn one-week-ago
  [date]
  #?(:cljs (-> date
               .valueOf
               (- week-ms)
               (js/Date.)
               .valueOf)
     :clj  (-> date
               (t/with-zone-same-instant "UTC")
               (t/minus (t/weeks 1))
               t/instant
               .toEpochMilli)))


(defn one-week-from-now
  [date]
  #?(:cljs (-> date
               .valueOf
               (+ week-ms)
               (js/Date.)
               .valueOf)
      :clj (-> date
               (t/with-zone-same-instant "UTC")
               (t/plus (t/weeks 1))
               t/instant
               .toEpochMilli)))


(defn set-hour-for-date [date hour zone]
  #?(:cljs (-> (js/moment.tz date zone)
               (.hour hour)
               (.startOf "hours")
               js/Date.)
     :clj  (-> date
               (t/with-zone-same-instant (t/zone-id zone))
               (t/adjust (t/local-time hour)) ;;See docs on adjust if unclear
               (t/with-zone-same-instant (t/zone-id "UTC")))))


(defn start-of-today [date zone]
  (set-hour-for-date date 0 zone))


(defn end-of-today [date zone]
  (set-hour-for-date date 20 zone)) ;;Set to 20 to avoid straddling the date line


(def time-range
  #?(:cljs (range (.valueOf (start-of-today (make-date) (get-default-timezone)))
                  (.valueOf (end-of-today (make-date) (get-default-timezone)))
                  hour-ms))
  #? (:clj (take-while #(t/before? % (end-of-today % (get-default-timezone)))
                       (iterate #(t/plus % (t/hours 1))
                                (start-of-today (make-date) (get-default-timezone))))))


(def time-set
  #?(:cljs (set (->> time-range
                     (map #(new js/Date %))))
     :clj  (set time-range)))


(defn date-string
 "creates a string in yyyy-mm-dd format from a js date obj"
 [date]
 (str (.getFullYear date) "-"
      (+ 1 (.getMonth date)) "-"
      (.getDate date)))


(defn zero-in-day
  "taking a date obj, or string, will return a new date object with Hours, Minutes, Seconds, and Milliseconds set to 0"
  [date]
  #?(:cljs (as-> date d
                 (if (string? d)
                   (clojure.string/replace d #"-" "/")      ;; sql needs "-" but js/Date does wierd time zone stuff unless the string uses "/"
                   d)
                 (js/Date. d)
                 (date-string d)
                 (js/Date. d))))

(defn get-ms
  "takes a js/date and returns milliseconds since 00:00 that day. Essentially relative ms for the day."
 [date]
  #?(:cljs (let [h  (.getHours date)
                 m  (.getMinutes date)
                 s  (.getSeconds date)
                 ms (.getMilliseconds date)]
             (+
               (-> h
                   (* 60)
                   (* 60)
                   (* 1000))
               (-> m
                   (* 60)
                   (* 1000))
               (-> s (* 1000))
               ms))))

(defn is-this-day-before-that-day? [this-day that-day]
  #?(:cljs (let [that-day-year  (.getFullYear that-day)
               that-day-month (.getMonth that-day)
               that-day-day   (.getDate that-day)

               this-day-year  (.getFullYear this-day)
               this-day-month (.getMonth this-day)
               this-day-day   (.getDate this-day)]

           (and (>= that-day-year this-day-year)
                (>= that-day-month this-day-month)
                (> that-day-day this-day-day)))))


(defn is-this-day-after-that-day? [this-day that-day]
  #?(:cljs (let [that-day-year  (.getFullYear that-day)
               that-day-month (.getMonth that-day)
               that-day-day   (.getDate that-day)

               this-day-year  (.getFullYear this-day)
               this-day-month (.getMonth this-day)
               this-day-day   (.getDate this-day)]

           (and (<= that-day-year this-day-year)
                (<= that-day-month this-day-month)
                (< that-day-day this-day-day)))))


;; TODO would this function not work given utc dates?
(defn before-today
  "Given a date will return true when the day is before today. if the day is today or later will return false."
  [day]
  #?(:cljs (is-this-day-before-that-day? day (new js/Date))))


(defn after-today
  "Given a date will return true when the day is after today. If the day is today or earlier will return false."
  [day]
  #?(:cljs (is-this-day-after-that-day? day (new js/Date))))


(defn straddles-this-date? [this-date start-date stop-date]
  #?(:cljs (let [this  (.valueOf this-date)
               start (.valueOf start-date)
               stop  (.valueOf stop-date)]
           (and (> stop this)
                (< start this)))))


(defn straddles-now? [start-date stop-date]
  #?(:cljs (straddles-this-date? (new js/Date) start-date stop-date)))
