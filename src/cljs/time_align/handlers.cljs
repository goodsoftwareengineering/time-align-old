(ns time-align.handlers
  (:require [time-align.db :as db]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx]]
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

(reg-event-fx
 :set-selected-period
 (fn [cofx [_ period-id]]
   (let [
         db (:db cofx)
         type (if (nil? period-id) nil :period)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}
         ]

     {:db (assoc-in db [:view :selected]
                    {:current-selection  curr
                     :previous-selection prev})
      :dispatch [:action-buttons-back]}
     )
   ))

(reg-event-fx
 :set-selected-queue
 (fn [cofx [_ period-id]]
   ;; TODO might need to set action-button state on nil to auto collapse
   (let [
         db (:db cofx)
         type (if (nil? period-id) nil :queue)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}
         ]

     {:db (assoc-in db [:view :selected]
                    {:current-selection  curr
                     :previous-selection prev})
      :dispatch [:action-buttons-back]}
     )
   ))

;; not using this yet VVV
(reg-event-fx
 :set-selected-task
 (fn [cofx [_ task-id]]
   (let [db (:db cofx)]

     {:db (assoc-in db [:view :selected ]
                    {:selected-type :task
                     :id            task-id})
      :dispatch [:action-buttons-back]}
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

(reg-event-fx
 :save-category-form
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         name (get-in db [:view :category-form-name])
         id (if-let [id (get-in db [:view :category-form-id])]
              id
              (random-uuid))
         color (utils/color-255->hex (get-in db [:view :category-form-color]))
         categories (:categories db)]

     {:db (assoc db :categories (conj categories {:id id :name name :color color
                                                  :tasks []}))
      :dispatch [:set-active-page {:page-id :home :type nil :id nil}]}
     )))

(reg-event-db
 :set-category-form-name
 (fn [db [_ name]]
   (assoc-in db [:view :category-form-name] name)))

(reg-event-db
 :set-task-form-category-id
 (fn [db [_ category-id]]
   (assoc-in db [:view :task-form :category-id] category-id)))

(reg-event-db
 :set-task-form-name
 (fn [db [_ name]]
   (assoc-in db [:view :task-form :name] name)))

(reg-event-db
 :set-task-form-description
 (fn [db [_ desc]]
   (assoc-in db [:view :task-form :description] desc)))

(reg-event-db
 :set-task-form-complete
 (fn [db [_ comp]]
   (assoc-in db [:view :task-form :complete] comp)))

(reg-event-fx
 :submit-task-form
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         task-form (get-in db [:view :task-form])
         task-id (if (some? (:id task-form))
                            (:id task-form)
                            (random-uuid))
         category-id (uuid (:category-id task-form))
         other-categories (->> db
                             (:categories)
                             (filter #(not= (:id %) category-id)))
         this-category (some #(if (= (:id %) category-id) %)
                             (:categories db))
         other-tasks (:tasks this-category)
         this-task {:id task-id
                    :name (:name task-form)
                    :description (:description task-form)
                    :complete (:complete task-form)
                    :actual-periods []
                    :planned-periods []}
         new-db (merge db
                       {:categories
                        (conj other-categories
                              (merge this-category
                                     {:tasks
                                      (conj other-tasks this-task)}))})]

     (if (some? category-id) ;; secondary, view should not dispatch when nil
       {:db new-db :dispatch [:set-active-page {:page-id :home}]}
       {:db db ;; TODO display some sort of error
        }))))

(reg-event-db
 :set-period-form-date
 (fn [db [_ [new-d start-or-stop]]]
   (let [o (get-in db [:view :period-form start-or-stop])]
     (if (some? o)

       (let [old-d (new js/Date o)]
         (do
           (.setFullYear old-d (.getFullYear new-d))
           (.setDate old-d (.getDate new-d))
           (assoc-in db [:view :period-form start-or-stop] old-d)))

       (assoc-in db [:view :period-form start-or-stop] new-d))
     )
   ))

(reg-event-db
 :set-period-form-time
 (fn [db [_ [new-s start-or-stop]]]
   (let [o (get-in db [:view :period-form start-or-stop])]
     (if (some? o)
       (let [old-s (new js/Date o)]
         (do
           (.setHours old-s (.getHours new-s))
           (.setMinutes old-s (.getMinutes new-s))
           (.setSeconds old-s (.getSeconds new-s))
           (assoc-in db [:view :period-form start-or-stop] old-s)
           ))
       (do
         (let [n (new js/Date)]
           (.setFullYear new-s (.getFullYear n))
           (.setDate new-s (.getDate n))
           (assoc-in db [:view :period-form start-or-stop] new-s)
           )
         )
       )
   )
 ))

(reg-event-db
 :set-period-form-description
 (fn [db [_ desc]]
   (assoc-in db [:view :period-form :description] desc)
   ))

(reg-event-db
 :set-period-form-task-id
 (fn [db [_ task-id]]
   (assoc-in db [:view :period-form :task-id] task-id))
   )
;; TODO catch start stop error in submission of period form
