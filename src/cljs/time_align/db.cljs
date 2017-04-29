(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]))

(s/def ::name string?)
(s/def ::email string?)
(s/def ::id (s/and
             int?
             #(> % 0)))
(s/def ::moment (s/and ;; time is int (ms epoch) and positive
                 int?
                 #(> % 0)))
(s/def ::start ::moment)
(s/def ::stop ::moment)
(s/def ::priority int?)
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
(s/def ::task (s/keys :req-un [::dependencies ::category]
                      :opt-un [::periods ::priority]))
(s/def ::tasks (s/coll-of ::task))
(s/def ::user (s/keys :req-un [::name ::id ::email]))
(s/def ::date ::moment)
(s/def ::categories (s/coll-of ::category))
(s/def ::filters (s/coll-of ::category))
(s/def ::order #{:category :name :priority})
(s/def ::ordering string?)
(s/def ::range (s/keys :req-un [::filters ::start ::stop]))
(s/def ::queue (s/keys :req-un [::filters ::ordering]))
(s/def ::page  #{:home})
(s/def ::view (s/keys :req-un [::range ::queue ::page]))
(s/def ::db (s/keys :req-un [::user ::tasks ::view ::categories]))

;; {
;;  :user {:id :name :email}
;;  :track [{} {} {}]
;;  :queue [{} {} {}]
;;  :view {:range {:filters :start :stop}
;;         :queue {:filters [] :ordering }
;;         :page}
;;  }

(def default-db
  (gen/generate (s/gen ::db)))
