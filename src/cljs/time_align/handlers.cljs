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
 :set-drawer-state
 (fn [db [_ new-bool]]
   (assoc-in db [:view :drawer] new-bool)))

(reg-event-db
 :set-selected-period
 (fn [db [_ period-id]]
   (assoc-in db [:view :selected ]
             {:selected-period period-id :selected-task nil})))

(reg-event-db
 :set-selected-task
 (fn [db [_ task-id]]
   (assoc-in db [:view :selected ]
             {:selected-period nil :selected-task task-id})))

(reg-event-db
 :move-selected-period
 (fn [db [_ new-start-time-ms]]
   (if (some? (get-in db [:view :selected :selected-period]))
     (let [
           p-id (get-in db [:view :selected :selected-period])
           task (->> (:tasks db)
                     (filter
                      (fn [task]
                        (and
                         (some? (:periods task))
                         (not (empty? (->> (:periods task)
                                           (filter #(= p-id (:id %)))))))))
                     (first))
           period (->> (:periods task)
                       (filter #(= p-id (:id %)))
                       (first))
           other-periods (->> (:periods task)
                              (remove #(= p-id (:id %))))
           other-tasks (->> (:tasks db)
                            (remove
                             (fn [t]
                               (and
                                (some? (:periods t))
                                (not (empty?
                                      (->> (:periods t)
                                           (filter #(= p-id (:id %))))))))))
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
           new-period (merge period {:start new-start :stop new-stop})
           new-periods (cons new-period other-periods)
           new-task (merge task {:periods new-periods})
           new-tasks (cons new-task other-tasks )
           ]

       (merge db {:tasks new-tasks})
       )
     (do (.log js/console "no period selected")
         db)
     )
   ))
