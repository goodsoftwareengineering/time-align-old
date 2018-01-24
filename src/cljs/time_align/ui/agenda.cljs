(ns time-align.ui.agenda
  (:require [re-frame.core :as rf]
            [time-align.history :as hist]
            [time-align.ui.common :as uic]
            [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.client-utilities :as cutils]
            [time-align.js-interop :as jsi]
            [cljs-react-material-ui.core :refer [color]]
            ))

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
                    [ui/list-item
                     {:style           (merge {:width "100%"}
                                              (if (and period-selected
                                                       (= (:id period)
                                                          selected-id))
                                                {:backgroundColor (color :grey-300)}
                                                {}))
                      :key             (:id period)
                      :leftIcon        (r/as-element (uic/mini-arc period))
                      :primaryText     (uic/period-list-item-primary-text period)
                      :secondaryText   (uic/period-list-item-secondary-text period)
                      :on-double-click (fn [e]
                                         (when period-selected
                                           (hist/nav! (str "/edit/period/" (:id period))))) ;; TODO should hist only be messed with in handler interceptor?

                      :onTouchTap      (if (and period-selected
                                                (= selected-id (:id period)))
                                         (fn [e]
                                           (rf/dispatch [:set-active-page
                                                         {:page-id :entity-forms
                                                          :type    :period
                                                          :id      (:id period)}]))
                                         (fn [e]
                                           (rf/dispatch
                                             [:set-selected-period (:id period)])))}])))]))

