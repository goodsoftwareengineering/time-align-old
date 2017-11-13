(ns time-align.db
  (:require #?(:clj  [clojure.spec.alpha :as s]
               :cljs [clojure.spec.alpha :as s])
            #?(:clj  [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])
            #?(:clj  [java-time :as t])
            [clojure.test.check.generators :as gen]
            [time-align.utilities :as utils]
            [clojure.string :as string])
  #?(:clj (:import java.util.UUID)))

#?(:clj (defn random-uuid [](java.util.UUID/randomUUID)))

(s/def ::name (s/and string? #(> 256 (count %))))
(s/def ::description string?)
(s/def ::email string?)
(s/def ::id uuid?)
(s/def ::moment #?(:cljs (s/with-gen inst? #(s/gen utils/time-set))
                   :clj  (s/with-gen t/zoned-date-time? #(s/gen utils/time-set))))
(s/def ::start ::moment)
(s/def ::stop ::moment)
(s/def ::priority int?)
(s/def ::period-type #{:planned :actual})
(s/def ::period (s/with-gen (s/and
                             (s/keys :req-un [::id ::period-type]
                                     :opt-un [::start ::stop ::description])

                             (fn [period]
                               ;; actual has timestamps
                               (if (= :actual (:period-type period))
                                 (and (contains? period :start)
                                      (contains? period :stop))
                                 false))

                             (fn [period]
                               ;; stop after start
                               (if (and
                                     (contains? period :start)
                                     (contains? period :stop))
                                 (> (.valueOf (:stop period))
                                    (.valueOf (:start period)))
                                 true)))

                  ;; generator uses a generated moment and adds a random amount of time to it
                  ;; < 2 hrs
                  #(gen/fmap (fn [moment]
                               (let [queue-chance  (> 0.5 (rand))
                                     desc-chance   (> 0.5 (rand))
                                     actual-chance (> 0.5 (rand))
                                     start        (.valueOf moment)
                                     stop         (->> start
                                                       (+ (rand-int (* 2 utils/hour-ms))))
                                     stamps       (if queue-chance
                                                    {}
                                                    #?(:cljs {:start (new js/Date start)
                                                              :stop (new js/Date stop)}))
                                     type         (if-not (empty? stamps)
                                                    (if actual-chance
                                                      {:period-type :actual}
                                                      {:period-type :planned}
                                                      )
                                                    {:period-type :planned}
                                                    )
                                     desc         (if desc-chance
                                                    {:description (gen/generate (s/gen ::description))}
                                                    {})]

                                 (merge stamps desc type {:id (random-uuid)})))
                             (s/gen ::moment))))

(s/def ::periods (s/coll-of ::period :gen-max 5 :min-count 1))
(s/def ::hex-digit (s/with-gen (s/and string? #(contains? (set "0123456789abcdef") %))
                      #(s/gen (set "0123456789abcdef"))))
(s/def ::hex-str (s/with-gen (s/and string? (fn [s] (every? #(s/valid? ::hex-digit %) (seq s))))
                   #(gen/fmap string/join (gen/vector (s/gen ::hex-digit) 6))))
(s/def ::color (s/with-gen
                 (s/and #(= "#" (first %))
                        #(s/valid? ::hex-str (string/join (rest %)))
                        #(= 7 (count %)))
                 #(gen/fmap
                   (fn [hex-str] (string/join (cons "#" hex-str)))
                   (s/gen ::hex-str))))
;; (s/def ::dependency ::id) ;; TODO do tasks and periods have dependencies how to validate that they point correctly?
;; (s/def ::dependencies (s/coll-of ::dependency))
(s/def ::complete boolean?)
;; think about adding a condition that queue tasks (no periods) have to have planned true
;; (? and priority)
;; tasks that are not planned (:actual) cannot have periods in the future
;; adding date support is going to need some cljc trickery
(s/def ::actual-period (s/and ::period
                              (fn [period]
                                (and (contains? period :start)
                                     (contains? period :stop)))))
(s/def ::actual-periods (s/coll-of ::actual-period :gen-max 5 :min-count 1))
(s/def ::planned-periods ::periods)
(s/def ::task (s/keys :req-un [::id ::name ::description ::complete ::periods]))
;; TODO complete check (all periods are planned/actual are passed)
(s/def ::tasks (s/coll-of ::task :gen-max 2 :min-count 1))
(s/def ::user (s/keys :req-un [::name ::id ::email]))
(s/def ::category (s/keys :req-un [::id ::name ::color ::tasks]))
(s/def ::categories (s/coll-of ::category :gen-max 3 :min-count 0))
(s/def ::type #{:category :task :period :queue})
(s/def ::type-or-nil (s/with-gen
                       (s/or :is-type ::type
                             :is-nil nil?)
                       #(gen/return nil)))
(s/def ::id-or-nil (s/with-gen
                       (s/or :is-id ::id
                             :is-nil nil?)
                       #(gen/return nil)))
(s/def ::page-id (s/with-gen #{:home :add-entity-forms :edit-entity-forms :list :queue :agenda}
                   #(gen/return :home)))
(s/def ::page  (s/keys :req-un [::page-id
                                ::type-or-nil
                                ::id-or-nil]))
(s/def ::current-selection (s/and (s/keys :req-un [::type-or-nil ::id-or-nil])
                                  (fn [sel] (if (some? (:type-or-nil sel))
                                              (some? (:id-or-nil sel))
                                              false))))
(s/def ::previous-selection (s/and (s/keys :req-un [::type-or-nil ::id-or-nil])
                                   (fn [sel] (if (some? (:type-or-nil sel))
                                               (some? (:id-or-nil sel))
                                               false))))
(s/def ::selected (s/keys :req-un [::current-selection
                                   ::previous-selection]))
(s/def ::main-drawer (s/with-gen
                       boolean?
                       #(gen/return false)))
(s/def ::moving-period (s/with-gen
                         boolean?
                         #(gen/return false)))
(s/def ::continous-action (s/keys :req-un [::moving-period]))
(s/def ::zoom (s/with-gen (s/or :zoomed-in #{:q1 :q2 :q3 :q4}
                                :zoomed-out nil?)
                #(gen/return nil)))
(s/def ::action-buttons (s/with-gen
                          #{:collapsed
                            :period
                            :queue
                            :no-selection} ;; depends on zoom

                          #(gen/return :collapsed)))
(s/def ::color-255 (s/with-gen (s/and int?
                                      (fn [i]
                                        (and (> 256 i)
                                             (<= 0 i))))
                     #(gen/return 0)))
(s/def ::red ::color-255)
(s/def ::blue ::color-255)
(s/def ::green ::color-255)
(s/def ::color-map (s/keys :req-un [::red ::blue ::green]))
(s/def ::category-form (s/with-gen
                         (s/keys :req-un [::id-or-nil ::name ::color-map])
                         #(gen/return {:id-or-nil nil
                                       :name ""
                                       :color-map {:red 0 :green 0 :blue 0}})))
(s/def ::category-id ::id-or-nil)
;; TODO figure out a better default for category-id
(s/def ::task-form (s/with-gen
                     (s/keys :req-un [::id-or-nil ::name ::description ::complete ::category-id])
                     #(gen/return {:id-or-nil nil
                                   :name ""
                                   :description ""
                                   :complete false
                                   :category-id nil})))
(s/def ::task-id ::id-or-nil)
(s/def ::error #{:time-mismatch})
(s/def ::error-or-nil (s/with-gen
                        (s/or :is-error ::error
                              :no-error nil?)
                        #(gen/return nil)))
(s/def ::planned boolean?)
(s/def ::period-form (s/with-gen
                       (s/keys :req-un [::id-or-nil ::task-id ::error-or-nil ::planned]
                               :opt-un [::start ::stop ::description])
                       #(gen/return {:id-or-nil nil
                                     :task-id nil
                                     :error-or-nil nil
                                     :planned false})))
(s/def ::dashboard-tab (s/with-gen #{:agenda :queue :stats}
                         #(gen/return :agenda)))
(s/def ::displayed-day (s/with-gen inst?
                        #?(:cljs #(gen/return (new js/Date))
                           :clj #(gen/return (t/zoned-date-time)))))
(s/def ::period-in-play ::id-or-nil)
(s/def ::view (s/and (s/keys :req-un [::page
                                      ::selected
                                      ::period-in-play
                                      ::dashboard-tab
                                      ::continous-action
                                      ::main-drawer
                                      ::zoom
                                      ::action-buttons
                                      ::category-form
                                      ::task-form
                                      ::period-form
                                      ::displayed-day
                                      ])
                     (fn [view]
                       (if (get-in
                            view
                            [:continous-action
                             :moving-period])

                         (= :period
                            (get-in
                             view
                             [:selected :current-selection
                              :type-or-nil]))
                         true))))
(s/def ::db (s/keys :req-un [::user ::view ::categories]))

(def default-db
{:user
 {:name "",
  :id (random-uuid),
  :email ""},
 :view
 {:dashboard-tab :agenda
  :period-in-play nil,
  :zoom nil,
  :selected
  {:current-selection
   {:type-or-nil nil,
    :id-or-nil nil},
   :previous-selection {:type-or-nil nil, :id-or-nil nil}},
  :page {:page-id :home, :type-or-nil nil, :id-or-nil nil},
  :period-form
  {:id-or-nil nil, :task-id nil, :error-or-nil nil, :planned false},
  :action-buttons :collapsed,
  :continous-action {:moving-period false},
  :displayed-day (utils/make-date),
  :category-form
  {:id-or-nil nil, :name "", :color-map {:red 0, :green 0, :blue 0}},
  :task-form
  {:id-or-nil nil,
   :name "",
   :description "",
   :complete false,
   :category-id nil},
  :main-drawer false},
 :categories (list)})

