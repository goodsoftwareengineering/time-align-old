(ns time-align.history
  (:require [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType])
  (:import goog.History))

(declare history)

(defn hook-browser-navigation! []
  (defonce history
    (doto (History.)
             (events/listen
               HistoryEventType/NAVIGATE
               (fn [event]
                 (secretary/dispatch! (.-token event))))
             (.setEnabled true))))

(defn nav! [token]
  (.setToken history token))

(defn get-current-location []
  (.getToken history))
