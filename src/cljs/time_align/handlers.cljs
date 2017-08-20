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
  (fn [db [_ params]]
    (let [page (:page-id params)
          type (:type params)
          id (:id params)]
      (case page
        :home (assoc-in db [:view :page] {:page-id page
                                          :type-or-nil nil
                                          :id-or-nil nil})
        :entity-forms (assoc-in db [:view :page]
                                {:page-id page
                                 :type-or-nil type
                                 :id-or-nil id})
        ;; default
        (assoc-in db [:view :page] {:page-id page
                                    :type-or-nil nil
                                    :id-or-nil nil})
        )
            )
    ))

(reg-event-db
 :set-zoom
 (fn [db [_ quadrant]]
   (assoc-in db [:view :zoom] quadrant)))

(reg-event-db
 :set-view-range-day
 (fn [db [_ _]]
   (assoc-in db [:view :range]
             {:start (new js/Date)
              :stop  (new js/Date)})))

(reg-event-db
 :set-view-range-week
 (fn [db [_ _]]
   (assoc-in db [:view :range]
             {:start (utils/one-week-ago)
              :stop  (new js/Date)})))

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
   ;; TODO might need to set action-button state on nil to auto collapse
   (let [type (if (nil? period-id) nil :period)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}]
     (assoc-in db [:view :selected]
               {:current-selection  curr
                :previous-selection prev})
     )
   ))

(reg-event-db
 :set-selected-queue
 (fn [db [_ period-id]]
   ;; TODO might need to set action-button state on nil to auto collapse
   (let [type (if (nil? period-id) nil :queue)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}]
     (assoc-in db [:view :selected]
               {:current-selection  curr
                :previous-selection prev})
     )
   ))

(reg-event-db
 :action-buttons-expand
 (fn [db [_ _]]
   (let [selection (get-in db [:view :selected :current-selection])
         s-type    (:type-or-nil selection)
         ab-state
         (cond
           (nil? s-type)      :no-selection
           (= :period s-type) :period
           (= :queue s-type)  :queue
           :else              :no-selection)]
     (assoc-in db [:view :action-buttons] ab-state))))

(reg-event-db
 :action-buttons-back
 (fn [db [_ _]]
   (let [cur-state (get-in db [:view :action-buttons])
         new-state
         (cond
           (= cur-state :no-selection) :collapsed
           :else :collapsed)]
     (assoc-in db [:view :action-buttons] new-state)
     )))

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
              :id            task-id})))

(defn fell-tree-with-period-id [db p-id]
  (let [periods  (utils/pull-periods db)
        period   (->> periods
                      (filter #(= p-id (:id %)))
                      (first))
        t-id     (:task-id period)
        c-id     (:category-id period)
        category (->> (:categories db)
                      (filter #(= c-id (:id %)))
                      (first))
        task     (->> (:tasks category)
                      (filter #(= t-id (:id %)))
                      (first))]
    {:category category
     :task     task
     :period   period}))

(defn period-selected? [db]
  (= :period (get-in db [:view :selected :current-selection :type-or-nil])))

(reg-event-db
 :move-selected-period
 (fn [db [_ mid-point-time-ms]]
   (if (period-selected? db)
     (let [
           p-id             (get-in db [:view :selected :current-selection :id-or-nil])
           chopped-tree     (fell-tree-with-period-id db p-id)
           task             (:task chopped-tree)
           t-id             (:id task)
           category         (:category chopped-tree)
           c-id             (:id category)
           _period          (:period chopped-tree)
           period-type      (:type _period)
           type-coll        (case period-type
                              :actual  :actual-periods
                              :planned :planned-periods)
           period           (dissoc _period :type)
           other-periods    (->> (type-coll task)
                                 (remove #(= p-id (:id %))))
           other-tasks      (->> (:tasks category)
                                 (remove #(= t-id (:id %))))
           other-categories (->> (:categories db)
                                 (remove #(= c-id (:id %))))
           period-length-ms (- (.valueOf (:stop period))
                               (.valueOf (:start period)))

           new-start-ms          (.max js/Math
                                       (- mid-point-time-ms (/ period-length-ms 2))
                                       0) ;; handles part of the tricky divisor
           new-stop-ms           (+ new-start-ms period-length-ms)
           zero-day              (utils/zero-in-day (:start period))
           new-start             (->> zero-day
                                      (.valueOf)
                                      (+ new-start-ms)
                                      (new js/Date))
           new-stop              (->> zero-day
                                      (.valueOf)
                                      (+ new-stop-ms)
                                      (new js/Date))
           new-period            (merge period
                                        {:start new-start :stop new-stop})
           new-periods           (cons new-period other-periods)
           new-task              (merge task {type-coll new-periods})
           new-tasks             (cons new-task other-tasks)
           new-category          (merge category {:tasks new-tasks})
           new-categories        (cons new-category other-categories)
           ]

       (if (>= (+ new-stop-ms) ;; handles other part of tricky divisor
               (- utils/ms-in-day 1))
         (do (.log js/console "must split task can't straddle")
             db)
         (merge db {:categories new-categories}))
       )
     (do (.log js/console "no period selected")
         db)
     )
   ))

(reg-event-db
 :set-category-form-color
 (fn [db [_ color]]
   (assoc-in db [:view :category-form-color]
             (merge (get-in db [:view :category-form-color])
                    color))
   ))

(reg-event-db
 :save-category-form
 (fn [db [_ _]]
   (let [name (get-in db [:view :category-form-name])
         id (if-let [id (get-in db [:view :category-form-id])]
              id
              (random-uuid))
         color (utils/color-255->hex (get-in db [:view :category-form-color]))
         categories (:categories db)]
     (assoc db :categories (conj categories {:id id :name name :color color
                                             :tasks []}))
     )))

(reg-event-db
 :set-category-form-name
 (fn [db [_ name]]
   (assoc-in db [:view :category-form-name] name)))
