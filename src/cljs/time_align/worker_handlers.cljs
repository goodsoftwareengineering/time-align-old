(ns time-align.worker-handlers
  (:require [re-frame.core :refer [dispatch reg-event-fx reg-fx]]
            [oops.core :refer [oget oset!]]))


(def succ-fail (atom {}))

(defn handle-request!
  [event]
  (let [data   (js->clj (oget event "data") :keywordize-keys true)
        state  (keyword (:state data))
        result (:result data)
        handled-by (-> data :handled-by keyword)
        succ-fail @succ-fail
        succ-fail-handlers (handled-by succ-fail)
        on-success (:on-success succ-fail-handlers)
        on-error   (:on-error succ-fail-handlers)]
    (if (= :success (keyword state))
      (dispatch [on-success result])
      (dispatch [on-error result]))))

(defn init!
  [worker]
  (oset! worker "onmessage" handle-request!))

(reg-fx
  :worker
  (fn worker-fx
    [{:keys [pool handler arguments on-success on-error] :as data}]
    (swap! succ-fail merge {handler {:on-success on-success
                                     :on-error on-error}})
    (.postMessage pool (clj->js {:arguments arguments
                                 :handler handler}))
    ))

(reg-event-fx
  :on-worker-fx-success
  (fn [_ [_ result]]
    (.debug js/console "success" result)))

(reg-event-fx
  :on-worker-fx-error
  (fn [_ [_ result]]
    (.debug js/console "error" result)))

(reg-event-fx
  :test-worker-fx
  (fn [coeffects [_ task]]
    (let [worker-pool (-> coeffects :db :worker-pool)
          task-with-pool (assoc task :pool worker-pool)]
      {:worker task-with-pool})))
