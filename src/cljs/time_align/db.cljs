(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            ))

(def default-db
  {:page :home})

;; time is int (ms epoch) and positive
(s/def ::moment (s/and int?
                       #(> % 0)))
(s/def ::start ::moment)
(s/def ::stop ::moment)
;; periods are valid when stop happens after start and the difference between
;; them ins't greater than 1 days worth of ms
(s/def ::period (s/and
                 (s/keys :req-un [::start ::stop])
                 #(> (:stop %) (:start %))
                 #(> 86400000 (- (:stop %) (:start %)))))
(s/def ::periods (s/coll-of ::period))
(s/def ::task (s/keys :req-un [::periods]))



(gen/generate (s/gen ::task))


