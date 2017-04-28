(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            ))

(def default-db
  {:page :home})

;; time is int (ms epoch) and positive
(s/def ::id (s/and
             int?
             #(> % 0)))
(s/def ::moment (s/and
                 int?
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
(s/def ::category (s/and
                   string?
                   #(> 256 (count %))))
(s/def ::dependency ::id)
(s/def ::dependencies (s/coll-of ::dependency))
(s/def ::task (s/keys :req-un [::periods ::dependencies ::category]))
(s/def ::tasks (s/coll-of ::task))
(s/def ::planned ::tasks) ;; periods not nil & in the future
(s/def ::queue ::tasks)   ;; periods nil
(s/def ::actual ::tasks)  ;; periods not nil & in the past
(s/def ::day (s/keys :req-un [::planned ::queue ::actual]))

(gen/generate (s/gen ::day))


