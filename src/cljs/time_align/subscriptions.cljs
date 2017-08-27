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
         start-ms   (.valueOf (utils/zero-in-day start-inst))
         stop-inst (get-in db [:view :range :stop])
         stop-ms   (.valueOf (utils/zero-in-day stop-inst))
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
   (get-in db [:view :category-form-color])))

(reg-sub
 :categories
 (fn [db _]
   (get-in db [:categories])))

(reg-sub
 :category-form-name
 (fn [db _]
   (get-in db [:view :category-form-name])))

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
