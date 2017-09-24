(ns time-align.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [time-align.utilities :as utils]

            [cljs.pprint :refer [pprint]]))

(reg-sub
  :page
  (fn [db _]
    (get-in db [:view :page])))

(reg-sub
  :main-drawer-state
  (fn [db _]
    (get-in db [:view :main-drawer])))

(reg-sub
  :tasks
  (fn [db _]
    (utils/pull-tasks db)))

(reg-sub
  :is-moving-period
  (fn [db _]
    (get-in db [:view :continous-action :moving-period])))

(reg-sub
  :zoom
  (fn [db _]
    (get-in db [:view :zoom])))

(reg-sub
  :action-buttons
  (fn [db _]
    (get-in db [:view :action-buttons])))

(reg-sub
  :visible-days
  (fn [db _]
    (let [start-inst (get-in db [:view :range :start])
          start-ms (.valueOf (utils/zero-in-day start-inst))
          stop-inst (get-in db [:view :range :stop])
          stop-ms (.valueOf (utils/zero-in-day stop-inst))
          days-in-ms (range start-ms
                            stop-ms
                            utils/day-ms)]

      (if (= start-ms stop-ms)
        (list (new js/Date start-ms))
        (map #(new js/Date %) days-in-ms)))))

(reg-sub
  :selected
  (fn [db _]
    (get-in db [:view :selected])))

(reg-sub
  :category-form-color
  (fn [db _]
    (get-in db [:view :category-form :color-map])))

(reg-sub
  :categories
  (fn [db _]
    (get-in db [:categories])))

(reg-sub
  :category-form-name
  (fn [db _]
    (get-in db [:view :category-form :name])))

(reg-sub
  :task-form-name
  (fn [db _]
    (get-in db [:view :task-form :name])))

(reg-sub
  :task-form-description
  (fn [db _]
    (get-in db [:view :task-form :description])))

(reg-sub
  :task-form-complete
  (fn [db _]
    (get-in db [:view :task-form :complete])))

(reg-sub
  :task-form-category-id
  (fn [db _]
    (get-in db [:view :task-form :category-id])))

(reg-sub
  :period-form-start
  (fn [db _]
    (get-in db [:view :period-form :start])))

(reg-sub
  :period-form-stop
  (fn [db _]
    (get-in db [:view :period-form :stop])))

(reg-sub
  :period-form-description
  (fn [db _]
    (get-in db [:view :period-form :description])))

(reg-sub
  :period-form-task-id
  (fn [db _]
    (get-in db [:view :period-form :task-id])))

(reg-sub
  :period-form-error
  (fn [db _]
    (get-in db [:view :period-form :error-or-nil])))

(reg-sub
  :period-form-planned
  (fn [db _]
    ;; (let [period-id (get-in db [:view :period-form :id-or-nil])]
    ;;   (if (some? period-id)
    ;;     (let [all-periods (utils/pull-periods db)
    ;;           this-period (->> all-periods
    ;;                            (some #(if (= (:id %) period-id) %)))
    ;;           is-planned (= :planned (:type this-period))]
    ;;       is-planned)
    ;;     true ;; default to true
    ;;     ))
    (get-in db [:view :period-form :planned])
    ))

(reg-sub
 :displayed-day
 (fn [db _]
   (get-in db [:view :displayed-day])))

(reg-sub
 :planned-time
 (fn [db _]
   (let [day (get-in db [:view :displayed-day])
         periods (->> db
                      (utils/pull-periods)
                      ;; only shows periods for the currently selected day
                      ;; TODO another subscription or an option here for all planned past _this_ moment
                      (filter (fn [period] (utils/period-in-day day period)))
                      (filter (fn [period] (= :planned (:type period))))
                      )
         total-time (reduce
                     (fn [running-total period]
                       (let [start (.valueOf (:start period))
                             stop (.valueOf (:stop period))
                             total (- stop start)]
                         (+ running-total total)
                         ;; TODO doesn't account for tasks that straddle a day
                         ))
                     0 periods)]

     total-time)))

(reg-sub
 :accounted-time
 (fn [db _]
   (let [day (get-in db [:view :displayed-day])
         periods (->> db
                      (utils/pull-periods)
                      (filter (fn [period] (utils/period-in-day day period)))
                      (filter (fn [period] (= :actual (:type period)))))

         total-time (reduce
                     (fn [running-total period]
                       (let [start (.valueOf (:start period))
                             stop (.valueOf (:stop period))
                             total (- stop start)]
                         (+ running-total total)
                         ;; TODO doesn't account for tasks that straddle a day
                         ))
                     0 periods)]
     total-time)))


(reg-sub
 :periods
 (fn [db _]
   (utils/pull-periods db)))
