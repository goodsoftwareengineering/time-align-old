(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            [time-align.utilities :as utils]

            [cljs.pprint :refer [pprint]]))

(s/def ::name string?)
(s/def ::description string?)
(s/def ::email string?)
(s/def ::id uuid?)
(s/def ::moment (s/with-gen inst?
                  #(s/gen utils/time-set)))
(s/def ::start ::moment)
(s/def ::stop ::moment)
(s/def ::type #{:actual :planned})
(s/def ::priority int?)
(s/def ::period (s/with-gen (s/and
                             (s/keys :req-un [::type ::id]
                                     :opt-un [::start ::stop])
                             (fn [period]
                               (cond
                                 (and (contains? period :start) (contains? period :stop))
                                 (> (.valueOf (:stop period)) (.valueOf (:start period)))

                                 (not (and (contains? period :start) (contains? period :stop)))
                                 (= :planned (:type period))

                                 :else false

                                 ))
                             )

                  ;; generator uses a generated moment and adds a random amount of time to it
                  ;; < 2 hrs
                  #(gen/fmap (fn [moment]
                               (let [queue-status (> 0.5 (rand))
                                     type (if (> 0.5 (rand)) :actual :planned)
                                     start (.valueOf moment)
                                     stop  (->> start
                                                (+ (rand-int (* 2 utils/hour-ms))))
                                     stamps (if queue-status
                                              {}
                                              {:start (new js/Date start)
                                               :stop (new js/Date stop)})]

                                 (merge stamps {:type type
                                                :id (random-uuid)})))
                             (s/gen ::moment))))
(s/def ::periods (s/coll-of ::period))
(s/def ::category (s/and string? #(> 256 (count %))))
(s/def ::dependency ::id)
(s/def ::dependencies (s/coll-of ::dependency))
(s/def ::complete boolean?)
;; think about adding a condition that queue tasks (no periods) have to have planned true
;; (? and priority)
;; tasks that are not planned (:actual) cannot have periods in the future
;; adding date support is going to need some cljc trickery
(s/def ::task (s/keys :req-un [::id ::category ::name ::description ::complete]
                      :opt-un [::periods]))
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
(s/def ::drawer boolean?)
(s/def ::selected-period (s/with-gen
                           (s/or :period-id ::id
                                 :none nil?)
                          #(gen/return nil)))
(s/def ::selected-task (s/with-gen
                         (s/or :task-id ::id
                               :none nil?)
                         #(gen/return nil)))
(s/def ::selected (s/and (s/keys :req-un [::selected-task ::selected-period])
                         #(not (and (contains? (:selected-task %) :period-id)
                                    (contains? (:selected-period %) :task-id)))))
(s/def ::view (s/keys :req-un [::range ::queue ::page ::drawer
                               ::selected-period ::selected]))
(s/def ::db (s/keys :req-un [::user ::tasks ::view ::categories]))

(def default-db (gen/generate (s/gen ::db)))
