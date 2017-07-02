(ns time-align.utilities)


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

(defn one-week-ago []
  (.valueOf
   (new js/Date (- (.valueOf (new js/Date)) week-ms))))

(defn one-week-from-now []
  (.valueOf
   (new js/Date (+ (.valueOf (new js/Date)) week-ms))))

(defn start-of-today []
  (-> (new js/Date)
      (.setHours 0)))

(defn end-of-today []
  (-> (new js/Date)
      (.setHours 20)))

;; (def time-range
;;   (range (one-week-ago) (one-week-from-now) hour-ms))

(def time-range
  (range (start-of-today) (end-of-today) hour-ms))

(def time-set
  (set (->> time-range
            (map #(new js/Date %)))))

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

(defn polar-to-cartesian [cx cy r angle]
  (let [angle-in-radians (-> angle
                             (- 90)
                             (* (/ (.-PI js/Math) 180)))]

    {:x (+ cx (* r (.cos js/Math angle-in-radians)))
     :y (+ cy (* r (.sin js/Math angle-in-radians)))}))

(defn ms-to-angle [ms]
  (* (/ 360 ms-in-day) ms))

(defn get-ms [date]
  (let [h  (.getHours date)
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
     ms)))

(defn period-in-day [day period]
  (if (not (nil? period)) ;; TODO add spec here
    (let [day-y   (.getFullYear day)
          day-m   (.getMonth day)
          day-d   (.getDate day)
          day-str (str day-y day-m day-d)

          start   (:start period)
          start-y (.getFullYear start)
          start-m (.getMonth start)
          start-d (.getDate start)
          start-str (str start-y start-m start-d)

          stop   (:stop period)
          stop-y (.getFullYear stop)
          stop-m (.getMonth stop)
          stop-d (.getDate stop)
          stop-str (str stop-y stop-m stop-d)]

      (or
       (= day-str start-str)
       (= day-str stop-str)))
    false))

(defn filter-periods [day tasks]
  (->> tasks
       (map
        (fn [task]
          (let [id (:id task)
                all-periods (:periods task)]

            (->> all-periods
                 (filter (partial period-in-day day)) ;; filter out periods not in day
                 (map #(assoc % :task-id id))))));; add task id to each period
       (filter #(< 0 (count %)))))

