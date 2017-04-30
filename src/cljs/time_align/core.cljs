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
  (/ ms 360))

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

(defn home-page []
  (let [tasks @(rf/subscribe [:tasks])
        days  @(rf/subscribe [:visible-days])]

    (pprint days)

    [:div
     {:style {:display "flex" :justify-content "flex-start"
              :flex-wrap "no-wrap"}}
     (->> days
          (map #(let [date-str (.toDateString %)
                      ms (get-ms %)
                      angle (ms-to-angle ms)
                      arc (describe-arc 40 40 40 0 90)]

                  [:svg {:key date-str :style {:display "inline-box"}
                         :width "100%" :viewBox "0 0 100 100"}
                   [:circle {:cx "40" :cy "40" :r "40"
                             :fill "grey"}]
                   [:path {:d arc
                           :stroke "black"
                           :stroke-width "4"
                           :fill "transparent"}]
                   ])))
     ]
    )
  )

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
