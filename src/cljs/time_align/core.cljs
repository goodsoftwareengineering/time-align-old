(ns time-align.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [time-align.ajax :refer [load-interceptors!]]
            [time-align.handlers]
            [time-align.subscriptions]
            [clojure.string :as string]

            [cljs.pprint :refer [pprint]]
            )
  (:import goog.History))

(defn polar-to-cartesian [cx cy r angle]
  (let [angle-in-radians (-> angle
                             (- 90)
                             (* (/ (.-PI js/Math) 180)))]

    {:x (+ cx (* r (.cos js/Math angle-in-radians)))
     :y (+ cy (* r (.sin js/Math angle-in-radians)))}))

(defn describe-arc [cx cy r start stop]
  (let [
        p-start (polar-to-cartesian cx cy r start)
        p-stop  (polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- stop start) 180) "0" "1")]

    (string/join " " ["M" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 1 (:x p-stop) (:y p-stop)])))

(def ms-in-day
  (->> 1
       (* 24)
       (* 60)
       (* 60)
       (* 1000)))

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

(defn home-page []
  (let [tasks @(rf/subscribe [:tasks])
        days  @(rf/subscribe [:visible-days])]

    [:div
     {:style {:display "flex" :justify-content "flex-start"
              :flex-wrap "no-wrap"}}
     (->> days
          (map (fn [day]
                 (let [date-str (.toDateString day)
                       ms (get-ms day)
                       angle (ms-to-angle ms)
                       col-of-col-of-periods (filter-periods day tasks)
                       ]

                   [:svg {:key date-str :style {:display "inline-box"}
                          :width "100%" :viewBox "0 0 100 100"}
                    [:circle {:cx "40" :cy "40" :r "40"
                              :fill "grey"}]

                    (->> col-of-col-of-periods
                         (map (fn [periods]
                                (pprint periods)
                                (->> periods
                                     (map #(let [id (:task-id %)
                                                 start-date (:start %)
                                                 start-ms (get-ms start-date)
                                                 start-angle (ms-to-angle start-ms)

                                                 stop-angle  (ms-to-angle (get-ms (:stop %)))
                                                 arc (describe-arc 40 40 40 start-angle stop-angle)]

                                             (pprint {:start-date start-date :start-ms start-ms :start-angle start-angle })
                                             [:path {:key (str id "-" start-ms)
                                                     :d arc
                                                     :stroke "black"
                                                     :stroke-width "4"
                                                     :fill "transparent"}]

                                             )
                                          )
                                     )
                                )))
                    ]
                   ))))]))

(def pages
  {:home #'home-page})

(defn page []
  [:div
   [(pages @(rf/subscribe [:page]))]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (hook-browser-navigation!)
  (mount-components))
