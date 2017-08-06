(ns time-align.handlers
  (:require [time-align.db :as db]
            [re-frame.core :refer [dispatch reg-event-db]]
            [time-align.utilities :as utils]
            ))

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :set-active-page
  (fn [db [_ page]]
    (assoc-in db [:view :page] page)))

(reg-event-db
 :set-zoom
 (fn [db [_ quadrant]]
   (assoc-in db [:view :zoom] quadrant)))

(reg-event-db
 :set-view-range-day
 (fn [db [_ _]]
   (assoc-in db [:view :range]
             {:start (new js/Date)
              :stop (new js/Date)})))

(reg-event-db
 :set-view-range-week
 (fn [db [_ _]]
   (assoc-in db [:view :range]
             {:start (utils/one-week-ago)
              :stop (new js/Date)})))

(reg-event-db
 :set-view-range-custom
 (fn [db [_ range]]
   (assoc-in db [:view :range] range)))

(reg-event-db
 :toggle-main-drawer
 (fn [db [_ _]]
   (update-in db [:view :main-drawer] not)))

(reg-event-db
 :set-main-drawer
 (fn [db [_ new-state]]
   (assoc-in db [:view :main-drawer] new-state)))

(reg-event-db
 :set-selected-period
 (fn [db [_ period-id]]
   (let [type (if (nil? period-id) nil :period)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}]
     (assoc-in db [:view :selected]
               {:current-selection curr
                :previous-selection prev})
     )
   ))

(reg-event-db
 :set-moving-period
 (fn [db [_ is-moving-bool]]
   (assoc-in db [:view :continous-action :moving-period]
             is-moving-bool)))

(reg-event-db
 :set-selected-task
 (fn [db [_ task-id]]
   (assoc-in db [:view :selected ]
             {:selected-type :task
              :id task-id})))

(defn fell-tree-with-period-id [db p-id]
  (let [periods (utils/pull-periods db)
        period (->> periods
                    (filter #(= p-id (:id %)))
                    (first))
        t-id (:task-id period)
        c-id (:category-id period)
        category (->> (:categories db)
                      (filter #(= c-id (:id %)))
                      (first))
        task (->> (:tasks category)
                  (filter #(= t-id (:id %)))
                  (first))]
    {:category category
     :task task
     :period period}))

(defn period-selected? [db]
  (= :period (get-in db [:view :selected :current-selection :type-or-nil])))

(reg-event-db
 :move-selected-period
 (fn [db [_ new-start-time-ms]]
   (if (period-selected? db)
     (let [
           p-id (get-in db [:view :selected :current-selection :id-or-nil])
           chopped-tree (fell-tree-with-period-id db p-id)
           task (:task chopped-tree)
           t-id (:id task)
           category (:category chopped-tree)
           c-id (:id category)
           _period (:period chopped-tree)
           period-type (:type _period)
           type-coll (case period-type
                       :actual :actual-periods
                       :planned :planned-periods)
           period (dissoc _period :type)
           other-periods (->> (type-coll task)
                              (remove #(= p-id (:id %))))
           other-tasks (->> (:tasks category)
                            (remove #(= t-id (:id %))))
           other-categories (->> (:categories db)
                                 (remove #(= c-id (:id %))))
           period-length-ms (- (.valueOf (:stop period))
                               (.valueOf (:start period)))
           new-start (->> (:start period)
                         (utils/zero-in-day)
                         (.valueOf)
                         (+ new-start-time-ms)
                         (new js/Date))
           new-stop (->> new-start
                        (.valueOf)
                        (+ period-length-ms)
                        (new js/Date))
           new-period (merge period
                             {:start new-start :stop new-stop})
           new-periods (cons new-period other-periods)
           new-task (merge task {type-coll new-periods})
           new-tasks (cons new-task other-tasks)
           new-category (merge category {:tasks new-tasks})
           new-categories (cons new-category other-categories)
           ]

       (if (>= (+ new-start-time-ms period-length-ms)
               (- utils/ms-in-day 1))
         (do (.log js/console "must split task can't straddle")
             db)
         (merge db {:categories new-categories}))
       )
     (do (.log js/console "no period selected")
         db)
     )
   ))
