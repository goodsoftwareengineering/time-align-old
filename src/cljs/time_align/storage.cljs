(ns time-align.storage
  (:require [cognitect.transit :as t]
            [cljsjs.filesaverjs]
            [com.cognitect.transit.types :as ty]))

(extend-type ty/UUID
  IUUID)

(defn str->
  [st]
  (let [t (t/reader :json {:handlers {"u" cljs.core/uuid}})]
    (t/read t st)))

(defn store->keys
  []
  (map #(-> (.key js/localStorage %) str->)
      (range (.-length js/localStorage))))

(defn key->transit-str
  [key]
  (let [w (t/writer :json)]
    (t/write w key)))

(defn transit-json->map
  [transit-str]
  (let [r (t/reader :json {:handlers {"u" cljs.core/uuid} })]
    (t/read r transit-str)))

(defn export-app-db
  []
  (js/saveAs (js/File. [(key->transit-str
                         ;; re-learn keeps putting itself in app-db
                         ;; ignore it by using select-keys
                         (select-keys @re-frame.db/app-db [:view :categories :user]))]
                       (str (.toLocaleString (new js/Date)) " time align export.json")
                       (clj->js {:type "application/json+transit;charset=utf-8"}))))
