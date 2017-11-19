(ns time-align.handlers
  (:require [time-align.db :as db]
            [re-frame.core :refer [dispatch
                                   reg-event-db
                                   reg-event-fx
                                   reg-fx
                                   ->interceptor]]
            [ajax.core :refer [GET POST]]
            [time-align.utilities :as utils]
            [time-align.client-utilities :as cutils]
            [time-align.storage :as store]
            [time-align.history :as hist]
            [time-align.worker-handlers]
            [oops.core :refer [oget oset! ocall]]
            [alandipert.storage-atom :refer [local-storage remove-local-storage!]]))

(def persist-ls
  (->interceptor
    :id :persist-to-localstorage
    :after (fn [context]
             (remove-local-storage! :app-db)
             (local-storage (atom (dissoc (get-in context [:effects :db]) :worker-pool)) :app-db)
             context)))
(def route
  (->interceptor
   :id :route-after-event
   :after (fn [context]
            (let [payload (get-in context [:effects :route]) ;; [:add "/example/of/payload"]
                                                             ;; [:back]
                  back (= :back (first payload))
                  add  (if (= :add (first payload))
                         (last payload))
                  effects (get-in context [:effects])]

              (if back
                (ocall js/history "back")
                (if (some? add)
                  (hist/nav! add)))

              (merge context {:effects (dissoc effects :route)})))))

(def send-analytic
  (->interceptor
    :id :send-analytic
    :before (fn [context]
             (let [event (get-in context [:coeffects :event])
                   dispatch-key (nth event 0)
                   payload (nth event 1 nil)]
               ;; (println "Before send")
               ;; (utils/thread-friendly-pprint! event)
               (dispatch [:test-worker-fx {:handler :send-analytic
                                           :on-success :on-worker-fx-success
                                           :on-error   :on-worker-fx-error
                                           :arguments  {:params  {:dispatch_key dispatch-key
                                                                  :payload      {:payload payload}}
                                                        :csrf-token js/csrfToken}}]))
              context)))


(reg-event-db
  :initialize-db
  [persist-ls send-analytic]
  (fn [_ _]
    (let [hot-garbage-let-var (if (some #(= :app-db %) (store/store->keys))
                                (->> :app-db
                                     store/key->transit-str
                                     (.getItem js/localStorage)
                                     store/transit-json->map)
                                db/default-db)
          hot-garbage-worker-pool (merge hot-garbage-let-var
                                         {:worker-pool (js/Worker. (if js/goog.DEBUG
                                                                     "/bootstrap_worker.js"
                                                                     "js/worker.js"))})]
      (time-align.worker-handlers/init! (:worker-pool hot-garbage-worker-pool))
      hot-garbage-worker-pool)))


(reg-event-fx
  :set-active-page
  [persist-ls send-analytic]
  (fn [cofx [_ params]]
    (let [db (:db cofx)
          page (:page-id params)
          type (:type params)
          id (:id params)
          view-page (case page
                      :edit-entity-forms {:page-id page
                                          :type-or-nil type
                                          :id-or-nil   id}
                      :add-entity-forms {:page-id page
                                          :type-or-nil type
                                          :id-or-nil   nil}
                      ;;default
                      {:page-id     page
                       :type-or-nil nil
                       :id-or-nil   nil})

          to-load (case type
                    :category [:load-category-entity-form id]
                    :task [:load-task-entity-form id]
                    :period [:load-period-entity-form id]
                    nil)
          ]
      (merge {:db (assoc-in db [:view :page] view-page)}
             (when (= page :add-entity-forms)
               {:dispatch [:clear-entities]})
             (if (some? id)
               {:dispatch to-load}
               {})))))

(reg-event-db
  :load-category-entity-form
  [persist-ls send-analytic]
  (fn [db [_ id]]
    (let [categories (:categories db)
          this-category (some #(if (= id (:id %)) %) categories)
          name (:name this-category)
          color (cutils/color-hex->255 (:color this-category))]
      (assoc-in db [:view :category-form]
                {:id-or-nil id
                 :name      name
                 :color-map color}))))

(reg-event-db
  :load-task-entity-form
  [persist-ls send-analytic]
  (fn [db [_ id]]
    (let [tasks (cutils/pull-tasks db)
          this-task (some #(if (= id (:id %)) %) tasks)
          id (:id this-task)
          name (str (:name this-task))
          description (str (:description this-task))
          complete (:complete this-task)
          category-id (:category-id this-task)
          ]
      (assoc-in db [:view :task-form]
                {:id-or-nil   id
                 :name        name
                 :description description
                 :complete    complete
                 :category-id category-id}))))

(reg-event-db
  :load-period-entity-form
  [persist-ls send-analytic]
  (fn [db [_ id]]
    (let [periods (cutils/pull-periods db)
          this-period (some #(if (= id (:id %)) %)
                            periods)
          is-planned (= :planned (:type this-period))
          task-id (:task-id this-period)
          start (:start this-period)
          stop (:stop this-period)
          description (:description this-period)]

      (assoc-in db [:view :period-form]
                {:id-or-nil    id
                 :task-id      task-id
                 :error-or-nil nil
                 :start        start
                 :stop         stop
                 :planned      is-planned
                 :description  description})
      )))

(reg-event-db
  :set-zoom
  [persist-ls send-analytic]
  (fn [db [_ quadrant]]
    (assoc-in db [:view :zoom] quadrant)))

(reg-event-db
  :set-view-range-day
  [persist-ls send-analytic]
  (fn [db [_ _]]
    (assoc-in db [:view :range]
              {:start (new js/Date)
               :stop  (new js/Date)})))

(reg-event-db
  :set-view-range-week
  [persist-ls send-analytic]
  (fn [db [_ _]]
    (assoc-in db [:view :range]
              {:start (utils/one-week-ago (js/Date.))
               :stop  (new js/Date)})))

(reg-event-db
  :set-view-range-custom
  [persist-ls send-analytic]
  (fn [db [_ range]]
    (assoc-in db [:view :range] range)))

(reg-event-db
  :toggle-main-drawer
  [persist-ls send-analytic]
  (fn [db [_ _]]
    (update-in db [:view :main-drawer] not)))

(reg-event-db
  :set-main-drawer
  [persist-ls send-analytic]
  (fn [db [_ new-state]]
    (assoc-in db [:view :main-drawer] new-state)))

(reg-event-db
 :set-selected-category
 (fn [db [_ id]]
   (assoc-in db [:view :selected]
             {:current-selection  {:type-or-nil :category
                                   :id-or-nil   id}
              :previous-selection (get-in db [:view :selected :current-selection])})))

(reg-event-fx
  :set-selected-queue
  [persist-ls send-analytic]
  (fn [cofx [_ period-id]]
    ;; TODO might need to set action-button state on nil to auto collapse
    (let [
          db (:db cofx)
          type (if (nil? period-id) nil :queue)
          prev (get-in db [:view :selected :current-selection])
          curr {:type-or-nil type :id-or-nil period-id}
          ]

      {:db       (assoc-in db [:view :selected]
                           {:current-selection  curr
                            :previous-selection prev})
       :dispatch [:action-buttons-back]}
      )
    ))

;; not using this yet VVV
(reg-event-fx
  :set-selected-task
  [persist-ls send-analytic]
  (fn [cofx [_ task-id]]
    (let [db (:db cofx)]

      {:db       (assoc-in db [:view :selected]
                           {
                            :current-selection {:type-or-nil :task
                                                :id-or-nil task-id}
                            :previous-selection (get-in db [:view :selected :current-selection])
                            })
       ;; :dispatch [:action-buttons-back] ;; no selecting task on home yet
       }
      )
    ))

(reg-event-fx
 :set-selected-period
 [persist-ls send-analytic]
 (fn [cofx [_ period-id]]
   (let [
         db (:db cofx)
         type (if (nil? period-id) nil :period)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}
         in-play-id (get-in db [:view :period-in-play])
         ]

     {:db       (assoc-in db [:view :selected]
                          {:current-selection  curr
                           :previous-selection prev})
      :dispatch-n (filter some? (list ;; TODO upgrade re-frame and remove filter
                                 (when (and (some? in-play-id)
                                            (= in-play-id period-id))
                                   [:pause-period-play])
                                 [:action-buttons-back]))
      }
     )
   ))

(reg-event-db
  :action-buttons-expand
  [persist-ls send-analytic]
  (fn [db [_ _]]
    (let [selection (get-in db [:view :selected :current-selection])
          s-type (:type-or-nil selection)
          ab-state
          (cond
            (nil? s-type) :no-selection
            (= :period s-type) :period
            (= :queue s-type) :queue
            :else :no-selection)]
      (assoc-in db [:view :action-buttons] ab-state))))

(reg-event-db
  :action-buttons-back
  [persist-ls send-analytic]
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
  [persist-ls send-analytic]
  (fn [db [_ is-moving-bool]]
    (assoc-in db [:view :continous-action :moving-period]
              is-moving-bool)))

(defn fell-tree-with-period-id [db p-id]
  (let [periods (cutils/pull-periods db)
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
     :task     task
     :period   period}))

(defn period-selected? [db]
  (= :period (get-in db [:view :selected :current-selection :type-or-nil])))

(reg-event-db
  :move-selected-period
  [persist-ls]
  (fn [db [_ mid-point-time-ms]]
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

           new-start-ms          (- mid-point-time-ms (/ period-length-ms 2))
           new-stop-ms           (+ new-start-ms period-length-ms)

            this-day              (as-> (get-in db [:view :displayed-day]) d
                                       (new js/Date
                                            (.getFullYear d)
                                            (.getMonth d)
                                            (.getDate d)))

           ;; straddled task could have negative `new-stop-ms`
           ;; + ing the time to this-day zeroed in will account for that
           ;; same for straddling the other direction

           new-start             (->> this-day
                                      (.valueOf)
                                      (+ new-start-ms)
                                      (new js/Date))
           new-stop              (->> this-day
                                      (.valueOf)
                                      (+ new-stop-ms)
                                      (new js/Date))
           new-period            (merge period
                                        {:start new-start :stop new-stop})

           ;; TODO if we don't user specter this is preferred to the massive
           ;; nested cons/merge mess in other places
           new-periods           (cons new-period other-periods)
           new-task              (merge task {type-coll new-periods})
           new-tasks             (cons new-task other-tasks)
           new-category          (merge category {:tasks new-tasks})
           new-categories        (cons new-category other-categories)
           ]

       (merge db {:categories new-categories})
       )

     (do (.log js/console "no period selected")
         db)
     )
   ))

(reg-event-db
  :set-category-form-color
  [persist-ls send-analytic]
  (fn [db [_ color]]
    (assoc-in db [:view :category-form :color-map]
              (merge (get-in db [:view :category-form :color-map])
                     color))
    ))

(reg-event-fx
  :save-category-form
  [persist-ls route send-analytic]
  (fn [cofx [_ _]]
    (let [db (:db cofx)
          name (get-in db [:view :category-form :name])
          id-or-nil (get-in db [:view :category-form :id-or-nil])
          id (if (some? id-or-nil)
               id-or-nil
               (random-uuid))
          color (cutils/color-255->hex (get-in db [:view :category-form :color-map]))
          categories (:categories db)
          other-categories (filter #(not (= id (:id %))) categories)
          this-category (some #(if (= id (:id %)) %) categories)
          tasks (:tasks this-category)
          ]

      {:db (assoc db :categories (conj other-categories
                                       (merge this-category {:id id :name name :color color})))
       :dispatch [:clear-category-form]
       :route [:back]
       }
      )))

(reg-event-db
 :clear-category-form
 [persist-ls send-analytic]
 (fn [db _]
   (assoc-in db [:view :category-form] {:id-or-nil nil :name "" :color-map {:red 0 :green 0 :blue 0}}))
 )

(reg-event-db
  :set-category-form-name
  [persist-ls send-analytic]
  (fn [db [_ name]]
    (assoc-in db [:view :category-form :name] name)))

(reg-event-db
  :set-task-form-category-id
  [persist-ls send-analytic]
  (fn [db [_ category-id]]
    (assoc-in db [:view :task-form :category-id] category-id)))

(reg-event-db
  :set-task-form-name
  [persist-ls send-analytic]
  (fn [db [_ name]]
    (assoc-in db [:view :task-form :name] name)))

(reg-event-db
  :set-task-form-description
  [persist-ls send-analytic]
  (fn [db [_ desc]]
    (assoc-in db [:view :task-form :description] desc)))

(reg-event-db
  :set-task-form-complete
  [persist-ls send-analytic]
  (fn [db [_ comp]]
    (assoc-in db [:view :task-form :complete] comp)))

(reg-event-fx
  :clear-entities
  [persist-ls send-analytic]
  (fn [cofx _]
    {:dispatch-n (list [:clear-category-form]
                       [:clear-task-form]
                       [:clear-period-form])
     :db (:db cofx)}))

(reg-event-fx
  :save-task-form
  [persist-ls route send-analytic]
  (fn [cofx [_ _]]
    (let [db (:db cofx)
          task-form (get-in db [:view :task-form])
          task-id (if (some? (:id-or-nil task-form))
                    (:id-or-nil task-form)
                    (random-uuid))
          old-category-id (->> (cutils/pull-tasks db)
                               (some #(if (= (:id %) task-id)
                                        (:category-id %))))
          old-category  (some #(if (= (:id %) old-category-id) %) (:categories db))
          old-category-filtered-tasks (filter #(not= task-id (:id %)) ;; removes task from old category
                                              (:tasks old-category))
          old-category-filtered (merge old-category {:tasks old-category-filtered-tasks})
          category-id (:category-id task-form)
          other-categories (remove nil?
                                   (conj
                                    (->> db
                                         (:categories)
                                         (remove #(= (:id %) category-id))
                                         (remove #(= (:id %) old-category-id))) ;; gets all categories but old and new

                                    (if (and (not= category-id old-category-id)
                                             (not (nil? old-category-id)))
                                      old-category-filtered))) ;; puts back old doesn't matter if there wasn't an old

          this-category (some #(if (= (:id %) category-id) %)
                              (:categories db))
          other-tasks (:tasks this-category)
          this-task {:id              task-id
                     :name            (:name task-form)
                     :description     (:description task-form)
                     :complete        (:complete task-form)
                     :actual-periods  []
                     :planned-periods []}
          new-db (merge db
                        {:categories
                         (conj other-categories
                               (merge this-category
                                      {:tasks
                                       (conj other-tasks this-task)}))})]

      (if (some? category-id)                               ;; secondary, view should not dispatch when nil

        {:db new-db
         :route [:back]
         :dispatch [:clear-task-form]
         }

        {:db db                                             ;; TODO display some sort of error
         }))))

(reg-event-db
 :clear-task-form
 [persist-ls send-analytic]
 (fn [db _]
   (assoc-in db [:view :task-form] {:id-or-nil   nil
                                    :name        ""
                                    :description ""
                                    :complete    false
                                    :category-id nil
                                    }))
 )

(reg-event-db
  :set-period-form-date
  [persist-ls send-analytic]
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
  [persist-ls send-analytic]
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
  [persist-ls send-analytic]
  (fn [db [_ desc]]
    (assoc-in db [:view :period-form :description] desc)
    ))

(reg-event-db
  :set-period-form-task-id
  [persist-ls send-analytic]
  (fn [db [_ task-id]]
    (assoc-in db [:view :period-form :task-id] task-id))
  )

(reg-event-fx
  :save-period-form
  [persist-ls route send-analytic]
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
          tasks (cutils/pull-tasks db)

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
          other-periods-actual (filter #(not (= (:id %) period-id)) (:actual-periods this-task))

          is-actual (:actual this-period)
          make-actual (and (not (or (nil? start)            ;; check for start and stop is to keep queue periods from being toggled actual
                                    (nil? stop)))           ;; if a user tries it will silently fail to toggle
                           ;; TODO throw an error message
                           (not (get-in db [:view :period-form :planned])))

          this-period-to-be-inserted (merge (dissoc this-period :actual)
                                            (merge {:id          period-id
                                                    :description (:description period-form)}
                                                   (if (and (nil? start)
                                                            (nil? stop))
                                                     {}     ;; start & stop with nil fucks shit up
                                                     ;;keys have to be absent for queue
                                                     {:start start :stop stop})))

          new-db (merge db ;; puts period where it needs to be
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
                                                                        other-periods-planned)}))}))})]

      (if (or (and (nil? start) (nil? stop))
              (< start-v stop-v))
        (if (some? task-id)
          {:db new-db
           :route [:back]
           }

          {:db (assoc-in db [:view :period-form :error-or-nil] :no-task)}

          )
        {:db (assoc-in db [:view :period-form :error-or-nil] :time-mismatch)}
        )
      )))

(reg-event-db
 :clear-period-form
 [persist-ls send-analytic]
 (fn [db _]
   (assoc-in db
             [:view :period-form]
             {:id-or-nil nil :task-id nil :error-or-nil nil :planned false})))

(reg-event-fx
  :delete-category-form-entity
  [persist-ls route send-analytic]
  (fn [cofx [_ _]]
    (let [db (:db cofx)
          category-id (get-in db [:view :category-form :id-or-nil])
          other-categories (->> db
                                (:categories)
                                (filter #(not (= (:id %) category-id))))
          new-db (merge db {:categories other-categories})
          ]
      {:db       new-db
       :dispatch [:clear-category-form]
       :route [:back]})))

(reg-event-fx
  :delete-task-form-entity
  [persist-ls route send-analytic]
  (fn [cofx [_ _]]
    (let [db (:db cofx)
          task-id (get-in db [:view :task-form :id-or-nil])
          this-task (->> db
                         cutils/pull-tasks
                         (some #(if (= task-id (:id %)) %)))
          category-id (:category-id this-task)
          categories (:categories db)
          this-category (->> categories
                             (some #(if (= category-id (:id %)) %)))
          other-categories (->> categories
                                (filter #(not (= category-id (:id %)))))

          other-tasks (->> this-category
                           (:tasks)
                           (filter #(not (= task-id (:id %)))))
          new-db (merge db {:categories (conj other-categories
                                              (merge this-category {:tasks other-tasks}))})
          ]
      {:db       new-db
       :dispatch [:clear-task-form]
       :route [:back]}
      )))

(reg-event-fx
  :delete-period-form-entity
  [persist-ls route send-analytic]
  (fn [cofx [_ _]]
    (let [db (:db cofx)
          period-id (get-in db [:view :period-form :id-or-nil])
          periods (cutils/pull-periods db)
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

          tasks (cutils/pull-tasks db)
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


      {:db         new-db
       :dispatch-n (list [:clear-period-form]
                         [:set-selected-period nil])
       :route [:back]}
      )))

(reg-event-db
  :set-period-form-planned
  [persist-ls send-analytic]
  (fn [db [_ is-planned]]
    (assoc-in db [:view :period-form :planned] is-planned)))

(reg-event-db
 :iterate-displayed-day
 [persist-ls send-analytic]
 (fn [db [_ direction]]
   (let [current (get-in db [:view :displayed-day])
         current-date (.getDate current)
         new-date (case direction
                     :next (+ current-date 1)
                     :prev (- current-date 1)
                     :next-week (+ current-date 7)
                     :prev-week (- current-date 7))
         new (as-> current day;; TODO ugly, not immutable, and not UTC O.O
                   (.valueOf day)
                   (new js/Date day)
                   (.setDate day new-date)
                   (new js/Date day))]

     (assoc-in db [:view :displayed-day]
               new))))

(reg-event-fx
 :play-period
 [persist-ls send-analytic]
 (fn [cofx [_ id]]
   (let [
         db (:db cofx)
         ;; find the period
         periods (cutils/pull-periods db)
         this-period (some #(if (= (:id %) id) %) periods)

         ;; copy the period
         new-id (random-uuid)
         start (new js/Date)
         stop  (as-> (new js/Date) d
                 (.setMinutes d (+ 1 (.getMinutes d))) ;; TODO adjustable increment
                 (new js/Date d)
                 );; TODO we need to abstract out this mutative toxin

         new-actual-period (merge
                            (select-keys this-period
                                         [:description])
                            {:id new-id
                             :start start
                             :stop stop})
         ;; TODO uuid gen funciton that checks for taken? or should that only be handled on the back end?

         ;; set up to place
         category-id (:category-id this-period)
         task-id (:task-id this-period)
         all-categories (:categories db)
         this-category (some #(if (= (:id %) category-id) %)
                             all-categories)
         other-categories (filter #(not (= (:id %) category-id))
                                  all-categories)
         all-tasks (:tasks this-category)
         this-task (some #(if (= (:id %) task-id) %)
                         all-tasks)
         other-tasks (filter #(not (= (:id %) task-id))
                             all-tasks)
         all-actual-periods (:actual-periods this-task)

         ;; place
         new-task (merge this-task
                         {:actual-periods (conj all-actual-periods new-actual-period)})
         new-category (merge this-category
                             {:tasks (conj other-tasks new-task)})
         new-db (merge db
                       {:categories (conj other-categories new-category)
                        :view (assoc (:view db) :period-in-play new-id)})
         ]
     {:db new-db
      :dispatch [:set-selected-period nil]}
     )
   ))

(reg-event-db
 :play-actual-or-planned-period
 [persist-ls send-analytic]
 (fn [db [_ id]]

   ))

(reg-event-db
 :update-period-in-play
 [persist-ls send-analytic]
 (fn [db [_ _]]
   (let [playing-period (get-in db [:view :period-in-play])
         is-playing (some? playing-period)
         id playing-period
         periods (cutils/pull-periods db)
         all-actual-periods (filter #(= (:type %) :actual) periods)
         this-period (some #(if (= (:id %) id) %) all-actual-periods)]

     (if (and is-playing (some? this-period))
       (let [old-stop (:stop this-period)
             now (new js/Date)
             new-stop (if  (> (.valueOf old-stop) ;; TODO probably remove
                              (.valueOf now))
                        old-stop
                        now)
             new-this-period (merge this-period
                                    {:stop new-stop}) ;; this is the whole point

             ;; all the extra stuff to put it back in
             category-id (:category-id this-period)
             task-id (:task-id this-period)

             all-categories (:categories db)
             this-category (some #(if (= (:id %) category-id) %)
                                 all-categories)
             other-categories (filter #(not (= (:id %) category-id))
                                      all-categories)
             all-tasks (:tasks this-category)
             this-task (some #(if (= (:id %) task-id) %)
                             all-tasks)
             other-tasks (filter #(not (= (:id %) task-id))
                                 all-tasks)
             other-actual-periods (->> this-task
                                       (:actual-periods)
                                       (filter #(not (= (:id %) id))))

             new-task (merge this-task
                             {:actual-periods (conj other-actual-periods
                                                    new-this-period)})
             new-category (merge this-category
                                 {:tasks (conj other-tasks new-task)})
             new-db (merge db
                           {:categories (conj other-categories new-category)})
             ]
         new-db
         )
       ;; no playing period
       db)
     )))

(reg-event-db
 :pause-period-play ;; TODO should probably be called stop
 [persist-ls send-analytic]
 (fn [db _]
   (assoc-in db [:view :period-in-play] nil) ;; TODO after specter add in an adjust selected period stop time
   ))

(reg-event-db
 :set-dashboard-tab
 [persist-ls send-analytic]
 (fn [db [_ tab]]
   (assoc-in db [:view :dashboard-tab] tab))
 )
