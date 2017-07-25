(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            [time-align.utilities :as utils]
            [clojure.string :as string]

            [cljs.pprint :refer [pprint]]
            ))

(s/def ::name (s/and string? #(> 256 (count %))))
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
                                     :opt-un [::start ::stop ::description])
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
(s/def ::periods (s/coll-of ::period :gen-max 5))
(s/def ::hex-digit (s/with-gen (s/and string? #(contains? (set "0123456789abcdef") %))
                      #(s/gen (set "0123456789abcdef"))))
(s/def ::hex-str (s/with-gen (s/and string? (fn [s] (every? #(s/valid? ::hex-digit %) (seq s))))
                   #(gen/return (->> (range 1 (rand-int 100) 1)
                                     (map (fn [_] (nth (seq "0123456789abcdef") (rand-int 15))))
                                     (string/join)))))
(s/def ::color (s/with-gen (s/and #(= "#" (first %))
                                  #(s/valid? ::hex-str (string/join (rest %)))
                                  #(= 7 (count %)))
                 #(gen/return (->> (range 1 7 1)
                                  (map (fn [_] (nth (seq "0123456789abcdef") (rand-int 15))))
                                  (string/join)
                                  (str "#")))))
;; (s/def ::dependency ::id) ;; TODO do tasks and periods have dependencies how to validate that they point correctly?
;; (s/def ::dependencies (s/coll-of ::dependency))
(s/def ::complete boolean?)
;; think about adding a condition that queue tasks (no periods) have to have planned true
;; (? and priority)
;; tasks that are not planned (:actual) cannot have periods in the future
;; adding date support is going to need some cljc trickery
(s/def ::task (s/keys :req-un [::id ::name ::description ::complete]
                      :opt-un [::periods]))
;; TODO complete check (all periods are planned/actual are passed)
(s/def ::tasks (s/coll-of ::task :gen-max 10))
(s/def ::user (s/keys :req-un [::name ::id ::email]))
(s/def ::date ::moment)
(s/def ::category (s/keys :req-un [::id ::name ::color ::tasks]))
(s/def ::categories (s/coll-of ::category :gen-max 5))
(s/def ::order #{:category :name :priority})
(s/def ::ordering string?)
(s/def ::range (s/and (s/keys :req-un [::start ::stop])
                      #(> (.valueOf (:stop %)) (.valueOf (:start %)))))
(s/def ::queue (s/keys :req-un [::ordering]))
(s/def ::page  #{:home})
(s/def ::drawer boolean?)
(s/def ::selected-type #{:category :task :period})
(s/def ::selected (s/with-gen (s/or :selection (s/keys :req-un [::selected-type ::id])
                                    :no-selection nil?)
                    #(gen/return nil)))
(s/def ::view (s/keys :req-un [::range ::queue ::page ::drawer ::selected]))
(s/def ::db (s/keys :req-un [::user ::view ::categories]))
(def default-db (gen/generate (s/gen ::db)))

