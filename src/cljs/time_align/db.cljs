(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]

            [cljs.pprint :refer [pprint]]
            ))

(s/def ::name string?)
(s/def ::description string?)
(s/def ::email string?)
(s/def ::id (s/and int? #(> % 0)))

;; TODO move this
;; <test-gen-deps>
(def week-ms (->> 1
                  (* 7)
                  (* 24)
                  (* 60)
                  (* 60)
                  (* 1000)))

(def day-ms
  (->> 1
       (* 24)
       (* 60)
       (* 60)
       (* 1000)))

(def hour-ms
  (->> 1
       (* 60)
       (* 60)
       (* 1000)))

(defn one-week-ago []
  (.valueOf
   (new js/Date (- (.valueOf (new js/Date)) week-ms))))

(defn one-week-from-now []
  (.valueOf
   (new js/Date (+ (.valueOf (new js/Date)) week-ms))))

(def time-range
  (range (one-week-ago) (one-week-from-now) hour-ms))

(def time-set
  (set (->> time-range
            (map #(new js/Date %)))))

;; </test-gen-deps>

(s/def ::moment (s/with-gen inst?
                  #(s/gen time-set)))
(s/def ::start ::moment)
(s/def ::stop ::moment)
(s/def ::priority int?)

;; TODO remove this
;; I was concerned about the length of a period.
;; Initially it seemed like they should not cross days
;; It will be simpler to allow any length of period and just snip the rendering of any
;; task to not render before or the start, or past the end, of a day.
;; Some tasks will be double rendering on days and that is fine. To determine if a task
;; is rendered on a day just test that the day is on or between the start and stop times.

(s/def ::period (s/with-gen (s/and
                             (s/keys :req-un [::start ::stop])
                             #(> (.valueOf (:stop %)) (.valueOf (:start %))))

                  ;; generator uses a generated moment and adds a random amount of time to it < 2 hrs
                  #(gen/fmap (fn [moment]
                               (let [start (.valueOf moment)
                                   stop  (->> start
                                              (+ (rand-int (* 2 hour-ms))))]
                               {:start (new js/Date start )
                                :stop (new js/Date stop)}))
                            (s/gen ::moment))))

(s/def ::periods (s/coll-of ::period))
(s/def ::category (s/and string? #(> 256 (count %))))
(s/def ::dependency ::id)
(s/def ::dependencies (s/coll-of ::dependency))
(s/def ::planned boolean?)
;; think about adding a condition that queue tasks (no periods) have to have planned true (? and priority)
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
(s/def ::range (s/keys :req-un [::filters ::start ::stop]))
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

(defn zero-in-day [date]
  (pprint date)
  (new js/Date (.getFullYear date) (.getMonth date) (.getDate date) 0 0 0 0))

(defn period-in-day [day period]
  (if (not (nil? period)) ;; TODO add spec here
    (let [z-day   (.valueOf (zero-in-day day))
          z-start (.valueOf (zero-in-day (:start period)))
          z-stop  (.valueOf (zero-in-day (:stop period)))]
      (or
       (= z-day z-start)
       (= z-day z-stop)))

    false)
  )

(defn filter-periods [day tasks]
  (map
   #(let [all-periods (:periods %)]
      (filter (partial period-in-day day)
              all-periods))
   tasks))

(map #(:periods %) (:tasks default-db))

(count (:tasks default-db))

(def test-period (gen/generate (s/gen ::period)))
(def test-task (gen/generate (s/gen ::task)))

(pprint test-period)
(pprint test-task)

(period-in-day (new js/Date 2017 04 06) test-period)

(->> (:tasks default-db)
     (filter-periods (new js/Date 2017 03 26))
     )
