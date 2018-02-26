(ns time-align.ui.agenda
  (:require [re-frame.core :as rf]
            [time-align.history :as hist]
            [time-align.ui.common :as uic]
            [reagent.core :as r]
            [time-align.ui.list :as list]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.client-utilities :as cutils]
            [time-align.js-interop :as jsi]
            [cljs-react-material-ui.core :refer [color]]))

(defn agenda [selected periods]
  (let [planned-periods (->> periods
                             (filter #(and (:planned %)
                                           (cutils/period-has-stamps %)))
                             (filter #(> (:stop %) (jsi/value-of (new js/Date)))))
        planned-periods-sorted (sort-by #(jsi/value-of (:start %)) planned-periods)
        period-selected (= :period
                           (get-in selected [:current-selection :type-or-nil]))
        selected-id (get-in selected [:current-selection :id-or-nil])]
    [ui/list {:style {:width "100%"}}
        (->> planned-periods-sorted
             (map (fn [period]
                    (list/list-item-period (:current-selection selected) period))))]))

