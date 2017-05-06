(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            [time-align.utilities :as utils]

            [cljs.pprint :refer [pprint]]))

(s/def ::name string?)
(s/def ::description string?)
(s/def ::email string?)
(s/def ::id (s/and int? #(> % 0)))
(s/def ::moment (s/with-gen inst?
                  #(s/gen utils/time-set)))
(s/def ::start ::moment)
(s/def ::stop ::moment)
(s/def ::priority int?)
(s/def ::period (s/with-gen (s/and
                             (s/keys :req-un [::start ::stop])
                             #(> (.valueOf (:stop %)) (.valueOf (:start %))))

                  ;; generator uses a generated moment and adds a random amount of time to it
                  ;; < 2 hrs
                  #(gen/fmap (fn [moment]
                               (let [start (.valueOf moment)
                                   stop  (->> start
                                              (+ (rand-int (* 2 utils/hour-ms))))]
                               {:start (new js/Date start )
                                :stop (new js/Date stop)}))
                            (s/gen ::moment))))
(s/def ::periods (s/coll-of ::period))
(s/def ::category (s/and string? #(> 256 (count %))))
(s/def ::dependency ::id)
(s/def ::dependencies (s/coll-of ::dependency))
(s/def ::planned boolean?)
;; think about adding a condition that queue tasks (no periods) have to have planned true
;; (? and priority)
;; tasks that are not planned (:actual) cannot have periods in the future
;; adding date support is going to need some cljc trickery
(s/def ::task (s/keys :req-un [::id ::category ::planned ::name ::description]
                      :opt-un [::dependencies ::periods ::priority]))
(s/def ::tasks (s/coll-of ::task))
(s/def ::user (s/keys :req-un [::name ::id ::email]))
(s/def ::date ::moment)
(s/def ::categories (s/coll-of ::category))
(s/def ::filters (s/coll-of ::category))
(s/def ::order #{:category :name :priority})
(s/def ::ordering string?)
(s/def ::range (s/and (s/keys :req-un [::filters ::start ::stop])
                      #(> (.valueOf (:stop %)) (.valueOf (:start %)))))
(s/def ::queue (s/keys :req-un [::filters ::ordering]))
(s/def ::page  #{:home})
(s/def ::view (s/keys :req-un [::range ::queue ::page]))
(s/def ::db (s/keys :req-un [::user ::tasks ::view ::categories]))

;; db
;; {
;;  :user {:id :name :email}
;;  :tasks [{:category :planned
;;           :dependencies :periods :priority}]
;;  :view {:range {:filters :start :stop}
;;         :queue {:filters [] :ordering }
;;         :page}
;;  }

(def default-db
  (gen/generate (s/gen ::db)))
