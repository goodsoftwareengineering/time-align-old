(ns time-align.ui.queue
  (:require [time-align.client-utilities :as cutils]
            [cljs-react-material-ui.reagent :as ui]
            [reagent.core :as r]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.icons :as ic]
            [time-align.history :as hist]
            [re-frame.core :as rf]
            [time-align.utilities :as utils]
            [time-align.ui.common :as uic]))

(defn queue [tasks selected]
  (let [periods-no-stamps (cutils/filter-periods-no-stamps tasks)
        sel               (:current-selection selected)
        period-selected   (= :queue (:type-or-nil sel))
        sel-id            (:id-or-nil sel)]
    [ui/list {:style {:width "100%"}}
     (->> periods-no-stamps
          (map (fn [period]
                 [ui/list-item
                  {:style           (merge {:width "100%"}
                                           (if (and (= :queue (:type-or-nil sel))
                                                    (= (:id period) (:id-or-nil sel)))
                                             {:backgroundColor (color :grey-300)}
                                             {}))
                   :key             (:id period)
                   :leftIcon        (r/as-element
                                      [ui/svg-icon [ic/action-list {:color (:color period)}]])
                   :primaryText     (uic/period-list-item-primary-text period)
                   :secondaryText   (uic/period-list-item-secondary-text period)
                   :on-double-click (fn [e]
                                      (when period-selected
                                        (hist/nav! (str "/edit/period/" (:id period)))))
                   :onTouchTap      (if (and period-selected
                                             (= sel-id (:id period)))
                                      (fn [e]
                                        (rf/dispatch [:set-active-page
                                                      {:page-id :add-entity-forms
                                                       :type    :period
                                                       :id      (:id period)}]))
                                      (fn [e]
                                        (rf/dispatch
                                          [:set-selected-queue (:id period)])))}])))]))
