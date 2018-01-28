(ns time-align.ui.calendar
  (:require [re-frame.core :as rf]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            ))

(def year 2018)
(def month 0)

(def days (->> (range 1 32)
               (map #(new js/Date year month %))))

(def data
  (->> (range 1 32)
       (map (fn [date-num]
              (->> (if (> (rand) 0.1) (range 1 15) '())
                   (map (fn [_] (let [start-hour (.floor js/Math (rand 20))
                                      start-minute (.floor js/Math (rand 50))
                                      stop-hour (.floor js/Math (+ (rand 3) start-hour))
                                      stop-minute (.floor js/Math (+ (rand 9) start-minute))
                                      colors (cons "#"
                                                   (map #(.floor js/Math (rand 9)) (range 1 7)))]
                                  {:start (new js/Date year month date-num start-hour start-minute)
                                   :stop (new js/Date year month date-num stop-hour stop-minute)
                                   :color (apply str colors)}))))))))

(def cell-width (* (/ 100 7)))  ;; ~14

(def cell-height (* (/ 100 5))) ;; 20

(defn indices
  "From [stack overflow](https://stackoverflow.com/a/8642069/5040125)"
  [pred coll]
  (keep-indexed #(when (pred %2) %1) coll))

(defn get-day
  "A monday 1 based index where sunday is 7"
  [date]
  (let [date (.getDay date)]
    (if (= date 0) 7 date)
    ))

(defn week-has-day [week {:keys [year month date]}]
  (not (empty? (indices (fn [day] (let [this-days-year  (.getFullYear day)
                                        this-days-month (.getMonth day)
                                        this-days-date  (.getDate day)]
                                    (and (= year this-days-year)
                                         (= month this-days-month)
                                         (= date this-days-date))))
                        week))))

(defn week-number [ts]
  (let [year                   (.getFullYear ts)
        month                  (.getMonth ts)
        date                   (.getDate ts)
        day                    (get-day ts)
        month-coll             (->> (range 1 32)
                                    (map #(new js/Date year month %))
                                    (filter #(and (= year (.getFullYear %))
                                                  (= month (.getMonth %)))))
        month-starts-monday    (= 1 (get-day (first month-coll)))
        partitioned-by-mondays (partition-by #(= (get-day %) 1) month-coll)
        fuser                  (fn [[monday rest-of-week]]
                                 (into rest-of-week monday))
        ;; help from this https://stackoverflow.com/a/12806697/5040125
        partitioned-by-weeks   (->> partitioned-by-mondays
                                    (#(if month-starts-monday
                                         %
                                         (rest %)))
                                    (partition-all 2)
                                    (map fuser)
                                    (#(if month-starts-monday
                                        %
                                        (cons
                                         (first partitioned-by-mondays)
                                         %))))]

    (first (indices
            #(week-has-day % {:year year :month month :date date})
            partitioned-by-weeks))))

(defn calendar [data]
  (let [displayed-day @(rf/subscribe [:displayed-day])
        dd-year (.getFullYear displayed-day)
        dd-month (.getMonth displayed-day)
        [year month] @(rf/subscribe [:displayed-month])
        days (->> (range 1 32)
                  (map #(new js/Date year month %))
                  (filter #(and (= year (.getFullYear %))
                                (= month (.getMonth %)))))]

    [:div
     {:style
      {:display "flex" :justify-content "center" :flex-wrap "wrap"}}

     [:div.navigation
      {:style {:display "flex" :justify-content "space-around"
               :flex-wrap "nowrap" :width "100%"}}
      [ui/icon-button
       {:onClick (fn [e] (rf/dispatch [:decrease-displayed-month]))}
       [ic/image-navigate-before]]

      [:span (str year "/" (inc month))]

      [ui/icon-button
       {:onClick (fn [e] (rf/dispatch [:advance-displayed-month]))}
       [ic/image-navigate-next]]]

     [:svg {:key "calendar-svg"
            :id "calendar-svg"
            :xmlns "http://www.w3.org/2000/svg"
            :version  "1.1"
            :style       {:display      "inline-box"
                          ;; this stops scrolling for moving period
                          :touch-action "pinch-zoom"}
            :width       "100%"
            :height      "100%"
            :viewBox      "0 0 100 100"}

      (map-indexed

       (fn [i d]
         (let [this-day-date (.getDate d)
               displayed-day-date (.getDate displayed-day)
               this-day-is-today (and (= this-day-date displayed-day-date)
                                      (= dd-year (.getFullYear d))
                                      (= dd-month (.getMonth d)))
               x (-> d (get-day) (- 1) (* cell-width))
               y (* cell-height (week-number d))]
           [:g {:transform (str "translate(" x " " y ")")
                :id  (.toDateString d)
                :key (.toDateString d)}
            [:rect {:x "0"
                    :y "0"
                    :width cell-width
                    :height cell-height
                    :fill "white"
                    :stroke "#bcbcbc" ;; TODO grey400 when global styles are in place
                    :stroke-width "0.10"}]
            [:circle {:cx 3 :cy 3 :r 2 :fill (if this-day-is-today
                                               "red"
                                               "blue")}]
            [:text {:x 3 :y 3.75
                    :text-anchor "middle"
                    :stroke "white" :stroke-width "0.1"
                    :fill "white" :font-size "2"} (.getDate d)]

            ;; (->> data
            ;;      (filter (fn [periods])))
            ]))

       days)]]
    ))


