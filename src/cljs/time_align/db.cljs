(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            ))

(def default-db
  {:page :home})

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
(s/def ::user (s/keys :req-u [::name ::id ::email]))


;; make tasks specific for planned queue and actual
;; validate should only be concerned with planned and actual being in the past or future
(defn validate-planned [db]
  (let [planned (:planned db)]
    (= 0
       (count (filter )))
    )
  )


(s/def ::db (s/and
             (s/keys :req-un [::user ::planned ::actual ::queue])
             (validate-planned)))

;; {
;;  :user {:id :name :email}
;;  :planned [{} {} {}]
;;  :queue   [{} {} {}]
;;  :actual  [{} {} {}]
;;  :view {:range [{:date :filters}]
;;         :filters []
;;         :queue {:filters [] :ordering }}
;;  }

(gen/generate (s/gen ::db))

