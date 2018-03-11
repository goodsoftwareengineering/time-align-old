(ns time-align.client-utilities
  (:require
    [clojure.string :as string]
    [time-align.utilities :as utils]
    [oops.core :refer [oget oset! ocall]]
    [com.rpl.specter :as specter
     :refer-macros [select select-one select-one! transform setval ALL if-path submap MAP-VALS filterer VAL NONE END]]
    ))

(defn polar-to-cartesian [cx cy r angle]
  (let [cx-float (js/parseFloat cx)
        cy-float (js/parseFloat cy)
        r-float (js/parseFloat r)
        angle-in-radians (-> angle
                             (- 90)
                             (* (/ (.-PI js/Math) 180)))]

    {:x (+ cx-float (* r-float (.cos js/Math angle-in-radians)))
     :y (+ cy-float (* r-float (.sin js/Math angle-in-radians)))}))

(defn ms-to-angle
  "takes milliseconds and returns angle in degrees"
  [ms]
  (* (/ 360 utils/ms-in-day) ms))

(defn angle-to-ms
  ;; takes angle in degrees and returns milliseconds
  [angle]
  (* (/ utils/ms-in-day 360) angle))

(defn period-has-stamps [period]
  (if (and (contains? period :start)
           (contains? period :stop))
    (not (or (nil? (:start period))
             (nil? (:stop period))))
    false))

(defn period-in-day [day period]
  (if (and
       (not (nil? period))
       (period-has-stamps period)) ;; TODO add spec here

    (let [day-y   (.getFullYear day)
          day-m   (.getMonth day)
          day-d   (.getDate day)
          day-str (str day-y day-m day-d)

          start (:start period)
          start-y (.getFullYear start)
          start-m (.getMonth start)
          start-d (.getDate start)
          start-str (str start-y start-m start-d)

          stop (:stop period)
          stop-y (.getFullYear stop)
          stop-m (.getMonth stop)
          stop-d (.getDate stop)
          stop-str (str stop-y stop-m stop-d)]
      (or

        ;; start or stop is on the day
        (= day-str start-str)
        (= day-str stop-str)

        ;; start and stop are on either side of the day
        (and
          ;; start is before day
          (utils/is-this-day-before-that-day? start day)

          ;; stop is after day
          (utils/is-this-day-after-that-day? stop day)
          )
        ))
    false))

(defn filter-out-stamps
  "Takes a keyword indicating type of period and the task. If the task contains periods of that type it will filter out the those types of periods with no stamps and return the task with that type of period collection modified."
  [type task]
  (->> (type task)
       (filter
        ;; TODO use period-has-stamps
        (fn [period] (and (contains? period :start)
                          (contains? period :stop))))
       ((fn [periods] (if (empty? periods)
                        (dissoc task type)
                        (merge task {type periods}))))))

(defn filter-periods-with-stamps
  "Takes a list of tasks and returns a list of tasks with only periods that have stamps."
  [tasks]
  (->> tasks
       (filter (fn [task]
                 (and
                  (contains? task :periods)
                  (some? (some #(period-has-stamps %) (:periods task))))))))

(defn filter-periods-no-stamps
  "Takes a list of tasks and returns a list of modified periods."
  [tasks]
  (let [periods
        (->> tasks
             (filter (fn [task] (contains? task :periods)))
             (map (fn [task]
                    (->> task
                         (:periods)
                         (filter #(:planned %))
                         (filter #(not (period-has-stamps %)))
                         (map
                           (fn [period] (merge period {:task-id   (:id task)
                                                       :task-name (:name task)
                                                       :color     (:color task)}))))))
             (flatten))]
    periods))

(defn modify-and-pull-periods
  "Takes a keyword indicating period type, and the task containing periods. Returns a collection of periods with parent task info."
  [tasks]
  (->> tasks
       (map
         (fn [task]
           (let [id (:id task)
                 periods (:periods task)
                 color (:color task)]
             (->> periods
                  (map #(assoc % :task-id id :color color))))))
       (flatten)))

(defn filter-periods-for-day
  "Takes a day and a list of tasks and returns a list of modified periods."
  [day tasks]
  (let [new-tasks (filter-periods-with-stamps tasks)
        periods (modify-and-pull-periods new-tasks)
        periods-filtered (->> periods
                              (filter (partial period-in-day day)))]

    periods-filtered))

;; (def tasks (pull-tasks @re-frame.db/app-db))
;; (identity tasks)
;; (def day (new js/Date))
;; (identity day)
;; (filter-periods-for-day day tasks)
;; (filter-periods-with-stamps tasks)

(defn client-to-view-box [id evt type]
  (let [pt (-> (ocall js/document "getElementById" id)
               (ocall "createSVGPoint"))
        ctm (-> evt
                (oget "target")
                (ocall "getScreenCTM"))]

       (oset! pt "x" (if (= :touch type)
                              (as-> evt e
                                    (oget e "touches")
                                    (ocall e "item" 0)
                                    (oget e "clientX"))
                              (oget evt "clientX")))
       (oset! pt "y" (if (= :touch type)
                              (as-> evt e
                                    (oget e "touches")
                                    (ocall e "item" 0)
                                    (oget e "clientY"))
                              (oget evt "clientY")))

    (let [trans-pt (ocall pt "matrixTransform" (ocall ctm "inverse"))]
      {:x (oget trans-pt "x") :y (oget trans-pt "y")})))

;; TODO point-to-centered-circle combined with point-to-angle and client-to-view-box to make a single call
;; get time on click utility function (refactor all usages of these supporting functions)

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

  (let [pi (.-PI js/Math)
        xa (.abs js/Math x)
        ya (.abs js/Math y)
        quadrant (cond ;; these are clockwise (zoom is counter clockwise)
                   (and (> x 0) (> y 0)) 1
                   (and (> x 0) (< y 0)) 2
                   (and (< x 0) (< y 0)) 3
                   (and (< x 0) (> y 0)) 4
                   :else 0)
        special (cond
                  (and (= x 0) (> y 0)) 0
                  (and (> x 0) (= y 0)) (-> pi (/ 2))
                  (and (= x 0) (< y 0)) pi
                  (and (< x 0) (= y 0)) (-> pi (/ 2) (* 3))
                  :else nil)
        angle-in-radians (if (some? special)
                           special
                           (case quadrant
                             1 (.atan js/Math (/ xa ya))
                             2 (-> (.atan js/Math (/ ya xa)) (+ (/ pi 2)))
                             3 (-> (.atan js/Math (/ xa ya)) (+ pi))
                             4 (-> (.atan js/Math (-> (/ ya xa))) (+ (-> pi (/ 2) (* 3))))
                             0))]

    (/ (* angle-in-radians 180) pi)))

(defn modify-periods [category-id task-id color periods]
  (->> periods
       (map (fn [period]
              (merge period {:color       color
                             :category-id category-id
                             :task-id     task-id})))))

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

(defn pull-periods [db]
 (->> (:categories db)
      (map (fn [category]
             (let [color       (:color category)
                   category-id (:id category)]
               (->>
                (:tasks category)
                (map (fn [task]
                       (let [task-id (:id task)
                             periods  (modify-periods
                                       category-id
                                       task-id
                                       color
                                       (:periods task))]
                         periods)))))))
      (flatten)
      (remove empty?)
      (remove nil?)))

(defn find-task-with-period [tasks period-id]
  (some (fn [t]
          (some (fn [p]
                  (if (= (:id p) period-id) t))
                (:periods t))) tasks))

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

    {:red r :green g :blue b}
    ))

(defn parse-overlapping-periods
  "Takes a flat collection of periods. Returns a collection of collections of modified periods such that each leaf collection contains the segments of periods that overlap. [{p1} {p2} {p3} ] => [[{p1} {p2.1}] [{p2.2}] [{p3}]]"
  [periods]

  )

(defn same-day? [day-a day-b]
  (and (= (.getFullYear day-a) (.getFullYear day-b))
       (= (.getMonth day-a) (.getMonth day-b))
       (= (.getDate day-a) (.getDate day-b))))
