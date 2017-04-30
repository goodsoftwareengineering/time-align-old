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

            [cljs.pprint :refer [pprint]]
            )
  (:import goog.History))

(defn home-page []
  (let [tasks @(rf/subscribe [:tasks])
        days  @(rf/subscribe [:visible-days])]

    (pprint days)

    [:div
     {:style {:display "flex" :justify-content "flex-start"
              :flex-wrap "no-wrap"}}
     (->> days
          (map #(let [date (.toDateString %)]
                  [:svg {:key date :style {:display "inline-box"}
                         :width "100%" :viewBox "0 0 100 100"}
                   [:circle {:cx "50" :cy "50" :r "40"
                             :fill "grey"}]])))
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
