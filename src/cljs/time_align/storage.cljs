(ns time-align.storage
  (:require [cognitect.transit :as t]
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
