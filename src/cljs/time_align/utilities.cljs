(ns time-align.utilities
  (:require
   [clojure.string :as string]))

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
  "taking a date obj, or string, will return a new date object with Hours, Minutes, Seconds, and Milliseconds set to 0"
  [date]
  (let [d (if (string? date)
            (clojure.string/replace date #"-" "/") ;; sql needs "-" but js/Date does wierd time zone stuff unless the string uses "/"
            date)]
    (new js/Date (date-string (new js/Date d)))))

(defn polar-to-cartesian [cx cy r angle]
  (let [cx-float (js/parseFloat cx)
        cy-float (js/parseFloat cy)
        r-float (js/parseFloat r)
        angle-in-radians (-> angle
                             (- 90)
                             (* (/ (.-PI js/Math) 180)))]

    {:x (+ cx-float (* r-float(.cos js/Math angle-in-radians)))
     :y (+ cy-float (* r-float(.sin js/Math angle-in-radians)))}))

(defn ms-to-angle
  "takes milliseconds and returns angle in degrees"
  [ms]
  (* (/ 360 ms-in-day) ms))

(defn angle-to-ms
  ;; takes angle in degrees and returns milliseconds
  [angle]
  (* (/ ms-in-day 360) angle))

(defn get-ms
  "takes a js/date and returns milliseconds since 00:00 that day"
  [date]
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

          start     (:start period)
          start-y   (.getFullYear start)
          start-m   (.getMonth start)
          start-d   (.getDate start)
          start-str (str start-y start-m start-d)

          stop     (:stop period)
          stop-y   (.getFullYear stop)
          stop-m   (.getMonth stop)
          stop-d   (.getDate stop)
          stop-str (str stop-y stop-m stop-d)]

      (or
       (= day-str start-str)
       (= day-str stop-str)))
    false))

(defn filter-out-stamps
  "Takes a keyword indicating type of period and the task. If the task contains periods of that type it will filter out the those types of periods with no stamps and return the task with that type of period collection modified."
  [type task]
  (->> (type task)
       (filter
        (fn [period] (and (contains? period :start)
                          (contains? period :stop))))
       ((fn [periods] (if (empty? periods)
                        (dissoc task type)
                        (merge task {type periods}))))))

(defn filter-periods-with-stamps
  "Takes a list of tasks and returns a list of tasks with only periods that have stamps."
  [tasks]
  (->> tasks
       (filter (fn [task] (or (contains? task :actual-periods)
                              (contains? task :planned-periods))))
       (map (partial filter-out-stamps :actual-periods))
       (map (partial filter-out-stamps :planned-periods))
       )
  )

(defn filter-periods-no-stamps
  "Takes a list of tasks and returns a list of modified periods."
  [tasks]
  (let [periods
        (->> tasks
             (filter (fn [task] (contains? task :planned-periods)))
             (map (fn [task]
                    (->> (:planned-periods task)
                         (filter
                          (fn [period] (and (not (contains? period :start))
                                            (not (contains? period :stop)))))
                         (map
                          (fn [period] (merge period {:task-id   (:id task)
                                                      :task-name (:name task)
                                                      :color     (:color task)}))))))
             (flatten))]
    periods))

(defn modify-and-pull-periods
  "Takes a keyword indicating period type, and the task containing periods. Returns a collection of periods with parent task info."
  [type tasks]
  (->> tasks
       (map
        (fn [task]
          (let [id      (:id task)
                periods (type task)
                color   (:color task)]
            (->> periods
                 (map #(assoc % :task-id id :color color))))))
       (flatten))
  )

(defn filter-periods-for-day
  "Takes a day and a list of tasks and returns a list of modified periods."
  [day tasks]
  (let [new-tasks        (filter-periods-with-stamps tasks)
        actual-periods   (modify-and-pull-periods :actual-periods new-tasks)
        actual-filtered  (->> actual-periods
                              (filter (partial period-in-day day)))
        planned-periods  (modify-and-pull-periods :planned-periods new-tasks)
        planned-filtered (->> planned-periods
                              (filter (partial period-in-day day)))]

    {:actual-periods  actual-filtered
     :planned-periods planned-filtered}))

(defn client-to-view-box [id evt type]
  (let [pt  (-> (.getElementById js/document id)
                (.createSVGPoint))
        ctm (-> evt
                (.-target)
                (.getScreenCTM))]

    (set! (.-x pt) (if (= :touch type)
                     (as-> evt e
                          (.-touches e)
                          (.item e 0)
                          (.-clientX e))
                     (.-clientX evt)))
    (set! (.-y pt) (if (= :touch type)
                     (as-> evt e
                          (.-touches e)
                          (.item e 0)
                          (.-clientY e))
                     (.-clientY evt)))

    (let [trans-pt (.matrixTransform pt (.inverse ctm))]
      {:x (.-x trans-pt) :y (.-y trans-pt)})))

(defn point-to-centered-circle
  "converts an x,y coordinate from svg viewbox where (0,0) is at the top left
  to a coordinate where (0,0) would be in the center"
  [{:keys [x y cx cy]}]
  (let [xt (- x cx)
        yt (if (>= y cy)
             (- 0 (- y cy))
             (- cy y))]
    {:x xt :y yt}))

(defn point-to-angle
  "expects map {:x number :y number}
  in the form of circle centered cartesian coords
  produces angle in degrees"
  [{:keys [x y]}]

  (let [pi               (.-PI js/Math)
        xa               (.abs js/Math x)
        ya               (.abs js/Math y)
        quadrant         (cond
                           (and (> x 0) (> y 0)) 1
                           (and (> x 0) (< y 0)) 2
                           (and (< x 0) (< y 0)) 3
                           (and (< x 0) (> y 0)) 4
                           :else                 0)
        special          (cond
                           (and (= x 0) (> y 0)) 0
                           (and (> x 0) (= y 0)) (-> pi (/ 2))
                           (and (= x 0) (< y 0)) pi
                           (and (< x 0) (= y 0)) (-> pi (/ 2) (* 3))
                           :else                 nil)
        angle-in-radians (if (some? special)
                           special
                           (case quadrant
                             1 (.atan js/Math (/ xa ya))
                             2 (-> (.atan js/Math (/ ya xa)) (+ (/ pi 2)))
                             3 (-> (.atan js/Math (/ xa ya)) (+ pi))
                             4 (-> (.atan js/Math (-> (/ ya xa))) (+ (-> pi (/ 2) (* 3))))
                             0))]

    (/ (* angle-in-radians 180) pi)))

(defn pull-tasks [db]
  (->> (:categories db)
       (map (fn [category]
              (let [color       (:color category)
                    category-id (:id category)]
                (->>
                 (:tasks category)
                 (map (fn [task] (merge task {:color color :category-id category-id})))))))
       (flatten)
       (remove nil?)
       (remove empty?)))

(defn modify-periods [category-id task-id color type periods]
  (->> periods
   (map (fn [period]
          (merge period {:color       color
                         :category-id category-id
                         :task-id     task-id
                         :type        type})))))

(defn pull-periods [db]
  (->> (:categories db)
       (map (fn [category]
              (let [color       (:color category)
                    category-id (:id category)]
                (->>
                 (:tasks category)
                 (map (fn [task]
                        (let [task-id (:id task)
                              actual  (modify-periods
                                       category-id
                                       task-id
                                       color
                                       :actual
                                       (:actual-periods task))
                              planned (modify-periods
                                       category-id
                                       task-id
                                       color
                                       :planned
                                       (:planned-periods task))]
                          (concat actual planned)
                          ))))
                )
              )
            )
       (flatten)
       (remove empty?)
       (remove nil?)
       )
  )

(defn pad-one-b-16
  "given a base 10 number it will convert to a base 16 string and pad one zero if necessary"
  [number]
  (as-> number n
    (.toString n 16)
    (#(if (= 1 (count n))
        (str "0" n)
        n))))

(defn color-gradient
  "given two hex string (\"#ffaabb\") colors and a percent as decimal (0.25), will return a hex string color that is at percent along a color gradient."
  [from to percent]
  (let [value-from (string/join (rest from))
        value-to (string/join (rest to))

        r-f (js/parseInt (subs value-from 0 2) 16)
        g-f (js/parseInt (subs value-from 2 4) 16)
        b-f (js/parseInt (subs value-from 4 6) 16)

        r-t (js/parseInt (subs value-to 0 2) 16)
        g-t (js/parseInt (subs value-to 2 4) 16)
        b-t (js/parseInt (subs value-to 4 6) 16)

        r-step (->> (- r-f r-t)
                    (.abs js/Math)
                    (* percent)
                    (.ceil js/Math))
        g-step (->> (- g-f g-t)
                    (.abs js/Math)
                    (* percent)
                    (.ceil js/Math))
        b-step (->> (- b-f b-t)
                    (.abs js/Math)
                    (* percent)
                    (.ceil js/Math))

        r-n (if (> r-f r-t) (- r-f r-step) (+ r-f r-step))
        g-n (if (> g-f g-t) (- g-f g-step) (+ g-f g-step))
        b-n (if (> b-f b-t) (- b-f b-step) (+ b-f b-step))
        ]
    (string/join "" ["#"
                     (pad-one-b-16 r-n)
                     (pad-one-b-16 g-n)
                     (pad-one-b-16 b-n)
                    ])))

(defn color-255->hex [{:keys [red blue green]}]
  (let [red-hx (pad-one-b-16 red)
        blue-hx (pad-one-b-16 blue)
        green-hx (pad-one-b-16 green)]
    (str "#" red-hx green-hx blue-hx)
    )
  )

(defn color-hex->255 [hex-str]
  (let [value (string/join (rest hex-str))

        r (js/parseInt (subs value 0 2) 16)
        g (js/parseInt (subs value 2 4) 16)
        b (js/parseInt (subs value 4 6) 16)
        ]

    {:red r :green g :blue b }
    ))

