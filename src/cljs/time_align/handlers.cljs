(ns time-align.handlers
  (:require [time-align.db :as db]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx]]
            [time-align.utilities :as utils]
            ))

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-fx
  :set-active-page
  (fn [cofx [_ params]]
    (let [db (:db cofx)
          page (:page-id params)
          type (:type params)
          id (:id params)
          view-page  (case page
                       :home {:page-id page
                              :type-or-nil nil
                              :id-or-nil nil}
                       :entity-forms {:page-id page
                                      :type-or-nil type
                                      :id-or-nil id}
                       :list {:page-id page
                              :type-or-nil nil
                              :id-or-nil nil}
                       ;; default
                       {:page-id page
                        :type-or-nil nil
                        :id-or-nil nil})
          to-load (case type
                        :category [:load-category-entity-form id]
                        :task [:load-task-entity-form id]
                        :period [:load-period-entity-form id]
                        nil)
          ]
      (merge {:db (assoc-in db [:view :page] view-page)}
             (if (some? id)
               {:dispatch to-load}
               {}))
      )
    ))

(reg-event-db
 :load-category-entity-form
 (fn [db [_ id]]
   (let [categories (:categories db)
         this-category (some #(if (= id (:id %)) %) categories)
         name (:name this-category)
         color (utils/color-hex->255 (:color this-category))]
     (assoc-in db [:view :category-form]
               {:id-or-nil id
                :name name
                :color-map color}))))

(reg-event-db
 :load-task-entity-form
 (fn [db [_ id]]
   (let [tasks (utils/pull-tasks db)
         this-task (some #(if (= id (:id %)) %) tasks)
         id (:id this-task)
         name (str (:name this-task))
         description (str (:description this-task))
         complete (:complete this-task)
         category-id (:category-id this-task)
         ]
     (assoc-in db [:view :task-form]
               {:id-or-nil id
                :name name
                :description description
                :complete complete
                :category-id category-id}))))

(reg-event-db
 :load-period-entity-form
 (fn [db [_ id]]
   (let [periods (utils/pull-periods db)
         this-period (some #(if (= id (:id %)) %)
                           periods)
         is-planned (= :planned (:type this-period))
         task-id (:task-id this-period)
         start (:start this-period)
         stop (:stop this-period)
         description (:description this-period)]

     (assoc-in db [:view :period-form]
               {:id-or-nil id
                :task-id task-id
                :error-or-nil nil
                :start start
                :stop stop
                :planned is-planned
                :description description})
     )))

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

(reg-event-db
 :set-selected
 (fn [db [_ {:keys [type id]}]]
   (assoc-in db [:view :selected]
             {:current-selection {:type-or-nil type
                                  :id-or-nil   id}
              :previous-selection (get-in db [:view :selected :current-selection])})))

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
   (assoc-in db [:view :category-form :color-map]
             (merge (get-in db [:view :category-form :color-map])
                    color))
   ))

(reg-event-fx
 :save-category-form
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         name (get-in db [:view :category-form :name])
         id (if-let [id (get-in db [:view :category-form :id-or-nil])]
              id
              (random-uuid))
         color (utils/color-255->hex (get-in db [:view :category-form :color-map]))
         categories (:categories db)
         other-categories (filter #(not (= id (:id %))) categories)
         this-category (some #(if (= id (:id %)) %) categories)
         tasks (:tasks this-category)
         ]

     {:db (assoc db :categories (conj other-categories
                                      (merge this-category {:name name :color color})))
      :dispatch [:set-active-page {:page-id :home :type nil :id nil}]}
     )))

(reg-event-db
 :set-category-form-name
 (fn [db [_ name]]
   (assoc-in db [:view :category-form :name] name)))

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
         category-id (:category-id task-form)
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

(reg-event-fx
 :save-period-form
 (fn [cofx [_ _]]
   (let [db (:db cofx)

         period-form (get-in db [:view :period-form])
         period-id (if (some? (:id-or-nil period-form))
                   (:id-or-nil period-form)
                   (random-uuid))
         start (:start period-form)
         stop (:stop period-form)
         start-v (if (some? start)
                   (.valueOf start))
         stop-v (if (some? stop)
                  (.valueOf stop))

         task-id (:task-id period-form)
         tasks (utils/pull-tasks db)

         category-id (->> tasks
                          (some #(if (= task-id (:id %)) %))
                          (:category-id)
                          )

         other-categories (->> db
                               (:categories)
                               (filter #(not (= (:id %) category-id))))
         this-category (->> db
                            (:categories)
                            (some #(if (= (:id %) category-id) %))
                            )

         other-tasks (->> this-category
                          (:tasks)
                          (filter #(not (= (:id %) task-id))))
         this-task (some #(if (= (:id %) task-id) %) tasks)

         is-this-planned-period (->> this-task
                                     (:planned-periods)
                                     (some #(if (= period-id (:id %)) %)))
         this-planned-period (assoc is-this-planned-period :actual false)

         is-this-actual-period (->> this-task
                                 (:actual-periods)
                                 (some #(if (= period-id (:id %)) %)))
         this-actual-period (assoc is-this-actual-period :actual true)

         this-period (if (some? is-this-planned-period)
                       this-planned-period

                       (if (some? is-this-actual-period)
                         this-actual-period

                         {}))
         other-periods-planned (filter #(not (= (:id %) period-id)) (:planned-periods this-task))
         other-periods-actual  (filter #(not (= (:id %) period-id)) (:actual-periods this-task))

         is-actual (:actual this-period)
         make-actual (and (not (or (nil? start)  ;; check for start and stop is to keep queue periods from being toggled actual
                                   (nil? stop))) ;; if a user tries it will silently fail to toggle
                                                 ;; TODO throw an error message
                          (not (get-in db [:view :period-form :planned])))

         this-period-to-be-inserted (merge (dissoc this-period :actual)
                                           (merge {:id period-id
                                                   :description (:description period-form)}
                                                  (if (and (nil? start)
                                                           (nil? stop))
                                                    {} ;; start & stop with nil fucks shit up
                                                       ;;keys have to be absent for queue
                                                    {:start start :stop stop})))

         new-db (assoc-in
                 ;; puts period where it needs to be
                 (merge db
                        {:categories
                         (conj other-categories
                               (merge this-category
                                      {:tasks
                                       (conj other-tasks
                                             (merge (dissoc this-task :category-id :color)
                                                    ;; below will handle when a period is being changed
                                                    ;; from actual to planned
                                                    ;; by always merging over both sets in a task
                                                    {:actual-periods (if make-actual
                                                                       (conj other-periods-actual
                                                                             this-period-to-be-inserted)
                                                                       other-periods-actual)}
                                                    {:planned-periods (if (not make-actual)
                                                                        (conj other-periods-planned
                                                                              this-period-to-be-inserted)
                                                                        other-periods-planned)}))}))})
                 ;; resets period form
                 [:view :period-form ]
                 {:id-or-nil nil :task-id nil :error-or-nil nil :planned false}) ;; TODO move to a dispatched event
         ]
     (if (or (and (nil? start) (nil? stop))
             (< start-v stop-v))
       (if (some? task-id)
         {:db new-db :dispatch [:set-active-page {:page-id :home}]}
         {:db (assoc-in db [:view :period-form :error-or-nil] :no-task)}
         )
       {:db (assoc-in db [:view :period-form :error-or-nil] :time-mismatch)}
       )
     )))

(reg-event-fx
 :delete-period-form-entity
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         period-id (get-in db [:view :period-form :id-or-nil])
         periods (utils/pull-periods db)
         this-period (some #(if (= period-id (:id %)) %) periods)
         type (:type this-period)
         is-actual (= type :actual)

         task-id (:task-id this-period)
         category-id (:category-id this-period)

         other-periods (->> periods
                            (filter #(and (= task-id (:task-id %))
                                          (not= period-id (:id %)))))
         other-planned (->> other-periods
                            (filter #(= :planned (:type %)))
                            (map #(dissoc % :type :category-id :task-id :color))
                            )
         other-actual (->> other-periods
                           (filter #(= :actual (:type %)))
                           (map #(dissoc % :type :category-id :task-id :color))
                           )

         tasks (utils/pull-tasks db)
         other-tasks (->> tasks
                          (filter
                           #(and (= category-id (:category-id %))
                                 (not= task-id (:id %))))
                          (map #(dissoc % :category-id :color)))
         this-task (->> tasks
                        (some #(if (= task-id (:id %)) %))
                        (#(dissoc % :category-id :color)))

         categories (:categories db)
         other-categories (filter #(not= category-id (:id %)) categories)
         this-category (some #(if (= category-id (:id %)) %) categories)
         new-db (merge db
                       {:categories
                        (conj other-categories
                              (merge this-category
                                     {:tasks
                                      (conj other-tasks (merge this-task (if is-actual
                                                                           {:actual-periods other-actual}
                                                                           {:planned-periods other-planned})))}

                                     ))})
         ]


     {:db new-db
      :dispatch-n (list [:set-active-page {:page-id :home}]
                        [:set-selected-period nil])}
     )))

(reg-event-db
 :set-period-form-planned
 (fn [db [_ is-planned]]
   (assoc-in db [:view :period-form :planned] is-planned)))
