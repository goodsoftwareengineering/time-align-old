(ns time-align.handlers
  (:require [clojure.spec.alpha :as spec]
            [expound.alpha :as expound]
            [time-align.db :as db]
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
            [alandipert.storage-atom :refer [local-storage remove-local-storage!]]
            [com.rpl.specter :as specter
             :refer-macros [select select-one select-one! transform setval ALL if-path submap MAP-VALS filterer VAL NONE END]]
            [clojure.pprint :as pprint]))

;; The event/interceptor lifecycle
;;                   [event-1    event-2   event-3]
;; event fires    -> :before -> :before -> :before ↓
;;                                                 Event happens
;;                                                 ↓
;; event complete <- :after  <- :after  <- :after <-

;; The shape of context
;; {:coeffects {:event [:some-id :some-param]
;;              :db    <original contents of app-db>}
;;  :effects   {:db <new value for app-db>
;;              :dispatch  [:an-event-id :param1]}
;;  :queue     <a collection of further interceptors>
;;  :stack     <a collection of interceptors already walked> }

(def persist-ls
  (->interceptor
   :id :persist-to-localstorage
   :after (fn [context]
            (remove-local-storage! :app-db)
            (local-storage (-> context
                               (get-in [:effects :db])
                               atom)
                           :app-db)
            context)))

(def route
  (->interceptor
   :id :route-after-event
   :after (fn [context]
            (let [payload (get-in context [:effects :route]) ;; [:add "/example/of/payload"]
                   ;; [:back]
                  back    (= :back (first payload))
                  add     (if (= :add (first payload))
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
             (let [event        (get-in context [:coeffects :event])
                   dispatch-key (nth event 0)
                   payload      (nth event 1 nil)]
                ;; (println "Before send")
                ;; (utils/thread-friendly-pprint! event)
               (dispatch [:test-worker-fx {:handler    :send-analytic
                                           :on-success :on-worker-fx-success
                                           :on-error   :on-worker-fx-error
                                           :arguments  {:params     {:dispatch_key dispatch-key
                                                                     :payload      {:payload payload}}
                                                        :csrf-token js/csrfToken}}]))
             context)))

(def validate-app-db
  (->interceptor
   :id :validate-app-db
   :after (fn [{:keys [coeffects effects queue stack] :as context}]
            (let [old-db (:db coeffects)
                  new-db (:db effects)]
              (if-not (spec/valid? ::db/db new-db)
                (do
                  (println "------------------------------------------------------")
                  (pprint/pprint {:dispatch (:dispatch effects)
                                  :event    (:event coeffects)})
                  (js/alert "There was an error and the last action didn't go through.")
                  (try
                    (throw
                     (pprint/pprint (expound/expound ::db/db new-db)))
                    (catch :default e
                      (pprint/pprint
                       {:error-expounding e
                        :spec-explain-data-instead (spec/explain-data ::db/db new-db)})))

                  (->> context
                       (setval [:effects :db] old-db)
                       (merge {:validation {:valid? false
                                            :explanation
                                            ;; reason for the try #broken-expound
                                            ;; when validation failed a custom validation
                                            ;; function that relied on an (s/or) spec
                                            ;; definition expound blew up see this
                                            ;; https://github.com/bhb/expound/issues/41
                                            ;; TODO when this ^^ github issue gets solved
                                            ;; remove the try
                                            (try
                                              (throw
                                               (expound/expound ::db/db new-db))
                                              (catch :default e
                                                 {:error-expounding e
                                                  :spec-explain-data-instead
                                                  (spec/explain-data ::db/db new-db)}))}})))
                (->> context
                     (merge {:validation {:valid? true
                                          :explanation nil}})))))))

(reg-fx :init-worker
        (fn [worker-src-url]
          (time-align.worker-handlers/init! (js/Worker. worker-src-url))))

(defn initialize-db [cofx _]
  (let [initial-db (if (some #(= :app-db %) (store/store->keys))
                              (->> :app-db
                                   store/key->transit-str
                                   (.getItem js/localStorage)
                                   store/transit-json->map)
                              db/default-db)]
    {:db initial-db
     :init-worker (if js/goog.DEBUG "/bootstrap_worker.js" "js/worker.js") }))

(reg-event-fx :initialize-db
 [persist-ls send-analytic validate-app-db] initialize-db)

(defn determine-page [page type id]
  (case page
    :edit-entity-forms {:page-id     page
                        :type-or-nil type
                        :id-or-nil   id}
    :add-entity-forms {:page-id     page
                       :type-or-nil type
                       :id-or-nil   nil}
    ;;default
    {:page-id     page
     :type-or-nil nil
     :id-or-nil   nil}))

(defn determine-dispatched [type id query-params]
  (if (some? id)
    (case type
      :category [:load-category-entity-form id]
      :task     [:load-task-entity-form id]
      :period   [:load-period-entity-form id]
      nil)
    (case type
      :category [:load-new-category-entity-form query-params]
      :task [:load-new-task-entity-form query-params]
      :period [:load-new-period-entity-form query-params]
      nil)))

(reg-event-fx
 :set-active-page
 [persist-ls send-analytic validate-app-db]
 (fn [cofx [_ params]]
   (let [db           (:db cofx)
         page         (:page-id params)
         type         (:type params)
         id           (:id params)
         query-params (:query-params params)
         view-page    (determine-page page type id)
         to-load      (determine-dispatched type id query-params)]

     (merge {:db (assoc-in db [:view :page] view-page)
             :dispatch-n (filter some? (list to-load))}))))

(reg-event-db
 :load-category-entity-form
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ id]]
   (let [categories    (:categories db)
         this-category (some #(if (= id (:id %)) %) categories)
         name          (:name this-category)
         color         (cutils/color-hex->255 (:color this-category))]
     (assoc-in db [:view :category-form]
               {:id-or-nil id
                :name      name
                :color-map color}))))

(reg-event-db
 :load-new-category-entity-form
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ query-params]]
   (let [] ;; TODO pull query-params
     (assoc-in db [:view :category-form]
               {:id-or-nil nil
                :name      ""
                :color-map {:red 0 :blue 0 :green 0}}))))



(reg-event-db
 :load-task-entity-form
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ id]]
   (let [tasks       (cutils/pull-tasks db)
         this-task   (some #(if (= id (:id %)) %) tasks)
         id          (:id this-task)
         name        (str (:name this-task))
         description (str (:description this-task))
         complete    (:complete this-task)
         category-id (:category-id this-task)]
     (assoc-in db [:view :task-form]
               {:id-or-nil   id
                :name        name
                :description description
                :complete    complete
                :category-id category-id}))))

(reg-event-db
 :load-new-task-entity-form
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ query-params]]
   (let [] ;; TODO parse query-params
     (assoc-in db [:view :task-form]
               {:id-or-nil   nil
                :name        ""
                :description ""
                :complete    false
                :category-id nil}))))

(reg-event-db
 :load-period-entity-form
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ id]]
   (let [periods     (cutils/pull-periods db)
         this-period (some #(if (= id (:id %)) %)
                           periods)
         is-planned  (:planned this-period)
         task-id     (:task-id this-period)
         start       (:start this-period)
         stop        (:stop this-period)
         description (:description this-period)]

     (assoc-in db [:view :period-form]
               (merge {:id-or-nil    id
                       :task-id      task-id
                       :error-or-nil nil
                       :planned      is-planned
                       :description  description}

                      (when (cutils/period-has-stamps this-period)
                        {:start        start
                         :stop         stop}))))))

(reg-event-db
 :load-new-period-entity-form
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ query-params]]
   (let [is-planned  (if (contains? query-params :planned )
                       (= "true" (:planned query-params))
                       true)
         task-id     (if (contains? query-params :task-id )
                       (uuid (:task-id query-params))
                       nil)
         start       (if (contains? query-params :start-time)
                       (->> query-params
                            :start-time
                            (js/parseInt)
                            (new js/Date))
                       nil)
         stop        (if (contains? query-params :stop-time)
                       (->> query-params
                            :stop-time
                            (js/parseInt)
                            (new js/Date))
                       nil)

         description (if (contains? query-params :description-time)
                       (:description query-params)
                       nil)]

     (pprint/pprint {:query-params query-params
              :start start})
     (assoc-in db [:view :period-form]
               (merge {:id-or-nil    nil
                       :task-id      task-id
                       :error-or-nil nil
                       :planned      is-planned
                       :description  description}

                      (when (some? start) {:start start})
                      (when (some? stop) {:stop stop}))))))

(reg-event-db
 :set-zoom
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ quadrant]]
   (assoc-in db [:view :zoom] quadrant)))

(reg-event-db
 :set-view-range-day
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ _]]
   (assoc-in db [:view :range]
             {:start (new js/Date)
              :stop  (new js/Date)})))

(reg-event-db
 :set-view-range-week
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ _]]
   (assoc-in db [:view :range]
             {:start (utils/one-week-ago (js/Date.))
              :stop  (new js/Date)})))

(reg-event-db
 :set-view-range-custom
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ range]]
   (assoc-in db [:view :range] range)))

(reg-event-db
 :toggle-main-drawer
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ _]]
   (update-in db [:view :main-drawer] not)))

(reg-event-db
 :set-main-drawer
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ new-state]]
   (assoc-in db [:view :main-drawer] new-state)))

(reg-event-db
 :set-selected-category
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ id]]
   (assoc-in db [:view :selected]
             {:current-selection  {:type-or-nil :category
                                   :id-or-nil   id}
              :previous-selection (get-in db [:view :selected :current-selection])})))

(reg-event-fx
 :set-selected-queue
 [persist-ls send-analytic validate-app-db]
 (fn [cofx [_ period-id]]
    ;; TODO might need to set action-button state on nil to auto collapse
   (let [db   (:db cofx)
         type (if (nil? period-id) nil :queue)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}]

     {:db       (assoc-in db [:view :selected]
                          {:current-selection  curr
                           :previous-selection prev})
      :dispatch [:action-buttons-back]})))

(reg-event-fx
 :set-selected-task
 [persist-ls send-analytic validate-app-db]
 (fn [cofx [_ task-id]]
   (let [db (:db cofx)]

     {:db       (assoc-in db [:view :selected]
                          {:current-selection {:type-or-nil :task
                                               :id-or-nil task-id}
                           :previous-selection (get-in db [:view :selected :current-selection])})
       ;; :dispatch [:action-buttons-back] ;; no selecting task on home yet
})))

(reg-event-fx
 :set-selected-period
 [persist-ls send-analytic validate-app-db]
 (fn [cofx [_ period-id]]
   (let [db (:db cofx)
         type (if (nil? period-id) nil :period)
         prev (get-in db [:view :selected :current-selection])
         curr {:type-or-nil type :id-or-nil period-id}
         in-play-id (get-in db [:view :period-in-play])]

     {:db       (assoc-in db [:view :selected]
                          {:current-selection  curr
                           :previous-selection prev})
      :dispatch-n (filter some? (list ;; TODO upgrade re-frame and remove filter
                                 (when (and (some? in-play-id)
                                            (= in-play-id period-id))
                                   [:pause-period-play])
                                 [:action-buttons-back]))})))

(reg-event-db
 :action-buttons-expand
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ _]]
   (let [selection (get-in db [:view :selected :current-selection])
         s-type    (:type-or-nil selection)
         ab-state
         (cond
           (nil? s-type) :no-selection
           (= :period s-type) :period
           (= :queue s-type) :queue
           :else :no-selection)]
     (assoc-in db [:view :action-buttons] ab-state))))

(reg-event-db
 :action-buttons-back
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ _]]
   (let [cur-state (get-in db [:view :action-buttons])
         new-state
         (cond
           (= cur-state :no-selection) :collapsed
           :else :collapsed)]
     (assoc-in db [:view :action-buttons] new-state))))

(reg-event-db
 :set-moving-period
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ is-moving-bool]]
   (assoc-in db [:view :continous-action :moving-period]
             is-moving-bool)))

(defn fell-tree-with-period-id [db p-id]
  (let [periods  (cutils/pull-periods db)
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
 [persist-ls validate-app-db]
 (fn [db [_ mid-point-time-ms]]
   (if (period-selected? db)
     (let [p-id             (get-in db [:view :selected :current-selection :id-or-nil])
           chopped-tree     (fell-tree-with-period-id db p-id)
           task             (:task chopped-tree)
           t-id             (:id task)
           category         (:category chopped-tree)
           c-id             (:id category)
           _period          (:period chopped-tree)
           period-type      (:type _period)
           period           (dissoc _period :type)
           other-periods    (->> (:periods task)
                                 (remove #(= p-id (:id %))))
           other-tasks      (->> (:tasks category)
                                 (remove #(= t-id (:id %))))
           other-categories (->> (:categories db)
                                 (remove #(= c-id (:id %))))
           period-length-ms (- (.valueOf (:stop period))
                               (.valueOf (:start period)))

           new-start-ms     (- mid-point-time-ms (/ period-length-ms 2))
           new-stop-ms      (+ new-start-ms period-length-ms)

           this-day         (as-> (get-in db [:view :displayed-day]) d
                                  (new js/Date
                                       (.getFullYear d)
                                       (.getMonth d)
                                       (.getDate d)))

            ;; straddled task could have negative `new-stop-ms`
            ;; + ing the time to this-day zeroed in will account for that
            ;; same for straddling the other direction

           new-start        (->> this-day
                                 (.valueOf)
                                 (+ new-start-ms)
                                 (new js/Date))
           new-stop         (->> this-day
                                 (.valueOf)
                                 (+ new-stop-ms)
                                 (new js/Date))
           new-period       (merge period
                                   {:start new-start :stop new-stop})

            ;; TODO if we don't user specter this is preferred to the massive
            ;; nested cons/merge mess in other places
           new-periods      (cons new-period other-periods)
           new-task         (merge task {:periods new-periods})
           new-tasks        (cons new-task other-tasks)
           new-category     (merge category {:tasks new-tasks})
           new-categories   (cons new-category other-categories)]

       (merge db {:categories new-categories}))

     (do (.log js/console "no period selected")
         db))))

(reg-event-db
 :set-category-form-color
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ color]]
   (assoc-in db [:view :category-form :color-map]
             (merge (get-in db [:view :category-form :color-map])
                    color))))

(reg-event-fx
 :save-category-form
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ _]]
   (let [db               (:db cofx)
         name             (get-in db [:view :category-form :name])
         id-or-nil        (get-in db [:view :category-form :id-or-nil])
         id               (if (some? id-or-nil)
                            id-or-nil
                            (random-uuid))
         color            (cutils/color-255->hex (get-in db [:view :category-form :color-map]))
         categories       (:categories db)
         other-categories (filter #(not (= id (:id %))) categories)
         this-category    (some #(if (= id (:id %)) %) categories)
         tasks            (:tasks this-category)]

     {:db       (assoc db :categories (conj other-categories
                                            (merge this-category {:id id :name name :color color})))
      :dispatch [:clear-category-form]
      :route    [:back]})))

(reg-event-db
 :clear-category-form
 [persist-ls send-analytic validate-app-db]
 (fn [db _]
   (assoc-in db [:view :category-form] {:id-or-nil nil :name "" :color-map {:red 0 :green 0 :blue 0}})))

(reg-event-db
 :set-category-form-name
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ name]]
   (assoc-in db [:view :category-form :name] name)))

(reg-event-db
 :set-task-form-category-id
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ category-id]]
   (assoc-in db [:view :task-form :category-id] category-id)))

(reg-event-db
 :set-task-form-name
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ name]]
   (assoc-in db [:view :task-form :name] name)))

(reg-event-db
 :set-task-form-description
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ desc]]
   (assoc-in db [:view :task-form :description] desc)))

(reg-event-db
 :set-task-form-complete
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ comp]]
   (assoc-in db [:view :task-form :complete] comp)))

(reg-event-fx
 :clear-entities
 [persist-ls send-analytic validate-app-db]
 (fn [cofx _]
   {:dispatch-n (list [:clear-category-form]
                      [:clear-task-form]
                      [:clear-period-form])
    :db         (:db cofx)}))

(reg-event-fx
 :save-task-form
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         task-form (get-in db [:view :task-form])
         task-id (if (some? (:id-or-nil task-form))
                   (:id-or-nil task-form)
                   (random-uuid))
         category-id (:category-id task-form)
         old-task (->> (cutils/pull-tasks db)
                       (some #(if (= (:id %) task-id) %)))
         old-category-id  (:category-id old-task)

         clean-task (merge old-task
                           {:id task-id} ;; needed for new tasks and harmless for old
                           (select-keys task-form [:name :complete :description]))

         new-db-removed-old-task (specter/setval
                                  [:categories specter/ALL
                                   #(= old-category-id (:id %))
                                   :tasks specter/ALL
                                   #(= task-id (:id %))]

                                  specter/NONE db)
         new-db (specter/setval
                 [:categories specter/ALL
                  #(= category-id (:id %))
                  :tasks specter/END]

                 [clean-task] new-db-removed-old-task)]

     (if (some? category-id)           ;; secondary, view should not dispatch when nil

       {:db       new-db
        :route    [:back]
        :dispatch [:clear-task-form]}

       {:db db                         ;; TODO display some sort of error
}))))

(reg-event-db
 :clear-task-form
 [persist-ls send-analytic validate-app-db]
 (fn [db _]
   (assoc-in db [:view :task-form] {:id-or-nil   nil
                                    :name        ""
                                    :description ""
                                    :complete    false
                                    :category-id nil})))

(reg-event-db
 :set-period-form-date
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ [new-d start-or-stop]]]
   (let [o (get-in db [:view :period-form start-or-stop])]
     (if (some? o)

       (let [old-d (new js/Date o)]
         (do
           (.setFullYear old-d (.getFullYear new-d))
           (.setDate old-d (.getDate new-d))
           (assoc-in db [:view :period-form start-or-stop] old-d)))

       (assoc-in db [:view :period-form start-or-stop] new-d)))))

(reg-event-db
 :set-period-form-time
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ [new-s start-or-stop]]]
   (let [o (get-in db [:view :period-form start-or-stop])]
     (if (some? o)
       (let [old-s (new js/Date o)]
         (do
           (.setHours old-s (.getHours new-s))
           (.setMinutes old-s (.getMinutes new-s))
           (.setSeconds old-s (.getSeconds new-s))
           (assoc-in db [:view :period-form start-or-stop] old-s)))
       (do
         (let [n (new js/Date)]
           (.setFullYear new-s (.getFullYear n))
           (.setDate new-s (.getDate n))
           (assoc-in db [:view :period-form start-or-stop] new-s)))))))

(reg-event-db
 :set-period-form-description
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ desc]]
   (assoc-in db [:view :period-form :description] desc)))

(reg-event-db
 :set-period-form-task-id
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ task-id]]
   (assoc-in db [:view :period-form :task-id] task-id)))

(reg-event-fx
 :save-period-form
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         form (get-in db [:view :period-form])
         start (:start form)
         stop (:stop form)
         start-v (if (some? start)
                   (.valueOf start))
         stop-v (if (some? stop)
                  (.valueOf stop))
         task-id (:task-id form)
         period-id (if (some? (:id-or-nil form))
                     (:id-or-nil form)
                     (random-uuid))
         old-period    (some #(if (= period-id (:id %)) %)
                             (cutils/pull-periods db))
         old-task-id (:task-id old-period)
         period (merge old-period {:id period-id}
                       (select-keys form [:start :stop :description :task-id :planned]))
         clean-period (select-keys period [:id
                                           (if (some? (:start form))
                                             :start)
                                           (if (some? (:stop form))
                                             :stop)
                                           :description
                                           :planned])
         removed-old-period-db (specter/setval
                                [:categories specter/ALL
                                 :tasks specter/ALL #(= old-task-id (:id %))
                                 :periods specter/ALL #(= period-id (:id %))]

                                specter/NONE db)
         new-db (specter/setval
                 [:categories specter/ALL :tasks specter/ALL #(= task-id (:id %))
                  :periods specter/END]

                 [clean-period]

                 removed-old-period-db)]

     (if (or (and (nil? start) (nil? stop))  ;; TODO add error state for not planned with no stamps
             (< start-v stop-v))
       (if (some? task-id)
         {:db    new-db
          :route [:back]}

         {:db (assoc-in db [:view :period-form :error-or-nil] :no-task)})
       {:db (assoc-in db [:view :period-form :error-or-nil] :time-mismatch)}))))

(reg-event-db
 :clear-period-form
 [persist-ls send-analytic validate-app-db]
 (fn [db _]
   (assoc-in db
             [:view :period-form]
             {:id-or-nil nil :task-id nil :error-or-nil nil :planned false})))

(reg-event-fx
 :delete-category-form-entity
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ _]]
   (let [db               (:db cofx)
         category-id      (get-in db [:view :category-form :id-or-nil])
         other-categories (->> db
                               (:categories)
                               (filter #(not (= (:id %) category-id))))
         new-db           (merge db {:categories other-categories})]
     {:db       new-db
      :dispatch [:clear-category-form]
      :route    [:back]})))

(reg-event-fx
 :delete-task-form-entity
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ _]]
   (let [db               (:db cofx)
         task-id          (get-in db [:view :task-form :id-or-nil])
         this-task        (->> db
                               cutils/pull-tasks
                               (some #(if (= task-id (:id %)) %)))
         category-id      (:category-id this-task)
         categories       (:categories db)
         this-category    (->> categories
                               (some #(if (= category-id (:id %)) %)))
         other-categories (->> categories
                               (filter #(not (= category-id (:id %)))))

         other-tasks      (->> this-category
                               (:tasks)
                               (filter #(not (= task-id (:id %)))))
         new-db           (merge db {:categories (conj other-categories
                                                       (merge this-category {:tasks other-tasks}))})]
     {:db       new-db
      :dispatch [:clear-task-form]
      :route    [:back]})))

(reg-event-fx
 :delete-period-form-entity
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ _]]
   (let [db               (:db cofx)
         period-id        (get-in db [:view :period-form :id-or-nil])
         periods          (cutils/pull-periods db)
         this-period      (some #(if (= period-id (:id %)) %) periods)

         task-id          (:task-id this-period)
         category-id      (:category-id this-period)
         new-db           (specter/setval
                           [:categories specter/ALL #(= category-id (:id %))
                            :tasks specter/ALL #(= task-id (:id %))
                            :periods specter/ALL #(= period-id (:id %))]

                           specter/NONE db)]

     {:db         new-db
      :dispatch-n (list [:clear-period-form]
                        [:set-selected-period nil])
      :route      [:back]})))

(reg-event-db
 :set-period-form-planned
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ is-planned]]
   (assoc-in db [:view :period-form :planned] is-planned)))

(reg-event-db
 :iterate-displayed-day
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ direction]]
   (let [current      (get-in db [:view :displayed-day])
         current-date (.getDate current)
         new-date     (case direction
                        :next (+ current-date 1)
                        :prev (- current-date 1)
                        :next-week (+ current-date 7)
                        :prev-week (- current-date 7))
         new          (as-> current day ;; TODO ugly, not immutable, and not UTC O.O
                            (.valueOf day)
                            (new js/Date day)
                            (.setDate day new-date)
                            (new js/Date day))]

     (assoc-in db [:view :displayed-day]
               new))))

(reg-event-fx
 :play-period
 [persist-ls send-analytic validate-app-db]
 (fn [cofx [_ id]]
   (let [db                 (:db cofx)
          ;; find the period
         periods            (cutils/pull-periods db)
         this-period        (some #(if (= (:id %) id) %) periods)

          ;; copy the period
         new-id             (random-uuid)
         start              (new js/Date)
         stop               (as-> (new js/Date) d
                                  (.setMinutes d (+ 1 (.getMinutes d))) ;; TODO adjustable increment
                                  (new js/Date d))    ;; TODO we need to abstract out this mutative toxin

         new-actual-period  (merge
                             (select-keys this-period
                                          [:description])
                             {:id    new-id
                              :start start
                              :stop  stop
                              :planned false})
          ;; TODO uuid gen funciton that checks for taken? or should that only be handled on the back end?

          ;; set up to place
         category-id        (:category-id this-period)
         task-id            (:task-id this-period)
         all-categories     (:categories db)
         this-category      (some #(if (= (:id %) category-id) %)
                                  all-categories)
         other-categories   (filter #(not (= (:id %) category-id))
                                    all-categories)
         all-tasks          (:tasks this-category)
         this-task          (some #(if (= (:id %) task-id) %)
                                  all-tasks)
         other-tasks        (filter #(not (= (:id %) task-id))
                                    all-tasks)
         periods (:periods this-task)

          ;; place
         new-task           (merge this-task
                                   {:periods (conj periods new-actual-period)})
         new-category       (merge this-category
                                   {:tasks (conj other-tasks new-task)})
         new-db             (merge db
                                   {:categories (conj other-categories new-category)
                                    :view       (assoc (:view db) :period-in-play new-id)})]
     {:db       new-db
      :dispatch [:set-selected-period nil]})))

(reg-event-db
 :play-actual-or-planned-period
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ id]]))

(reg-event-db
 :update-period-in-play
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ _]]
   (let [playing-period     (get-in db [:view :period-in-play])
         is-playing         (some? playing-period)
         id                 playing-period
         periods            (cutils/pull-periods db)
         all-actual-periods (filter #(not (:planned %)) periods)
         this-period        (some #(if (= (:id %) id) %) all-actual-periods)]

     (if (and is-playing (some? this-period))
       (let [old-stop             (:stop this-period)
             now                  (new js/Date)
             new-stop             (if (> (.valueOf old-stop) ;; TODO probably remove
                                         (.valueOf now))
                                    old-stop
                                    now)
             new-this-period      (merge this-period
                                         {:stop new-stop}) ;; this is the whole point

              ;; all the extra stuff to put it back in
             category-id          (:category-id this-period)
             task-id              (:task-id this-period)

             all-categories       (:categories db)
             this-category        (some #(if (= (:id %) category-id) %)
                                        all-categories)
             other-categories     (filter #(not (= (:id %) category-id))
                                          all-categories)
             all-tasks            (:tasks this-category)
             this-task            (some #(if (= (:id %) task-id) %)
                                        all-tasks)
             other-tasks          (filter #(not (= (:id %) task-id))
                                          all-tasks)
             other-periods (->> this-task
                                (:periods)
                                (filter #(not (= (:id %) id))))

             new-task             (merge this-task
                                         {:periods (conj other-periods
                                                         new-this-period)})
             new-category         (merge this-category
                                         {:tasks (conj other-tasks new-task)})
             new-db               (merge db
                                         {:categories (conj other-categories new-category)})]
         new-db)
        ;; no playing period
       db))))

(reg-event-db
 :pause-period-play                    ;; TODO should probably be called stop
 [persist-ls send-analytic validate-app-db]
 (fn [db _]
   (assoc-in db [:view :period-in-play] nil) ;; TODO after specter add in an adjust selected period stop time
))

(reg-event-db
 :set-dashboard-tab
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ tab]]
   (assoc-in db [:view :dashboard-tab] tab)))

(reg-event-db
 :set-inline-period-long-press
 [persist-ls send-analytic validate-app-db]
 (fn [db [_ long-press-state]]
   (assoc-in db [:view :inline-period-long-press] long-press-state)))

(reg-event-fx
 :set-inline-period-add-dialog
 [persist-ls route send-analytic validate-app-db]
 (fn [cofx [_ val]]
   (let [db       (:db cofx)
         date-obj (get-in db [:view :inline-period-long-press :press-time])
         time     (if (some? date-obj)
                    (.valueOf date-obj))
         route-str (str "/add/period"
                        (when time (str "?start-time=" time)))]

     (merge {:db (assoc-in db [:view :inline-period-add-dialog] val)}
            (when val {:dispatch [:set-inline-period-long-press {:press-time nil
                                                                 :callback-id nil
                                                                 :press-on false}]
                       :route [:add route-str]})))))
