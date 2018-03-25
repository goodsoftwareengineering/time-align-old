(ns time-align.experimentation.components
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [re-learn.core :as re-learn]
            [time-align.ui.svg-day-view :as day-view]
            [time-align.ui.app-bar :as ab :refer [app-bar]]
            [time-align.history :as hist]
            [time-align.ui.queue :as qp]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [time-align.ui.calendar :as cp]
            [time-align.ui.common :as uic]
            [time-align.client-utilities :as cutils]
            [time-align.utilities :as utils]
            [time-align.ui.action-buttons :as actb]
            [time-align.ui.agenda :as ap]
            [time-align.ui.list :as lp]
            [time-align.js-interop :as jsi]))

(defn _filter-chips [style chips rm-fn]
  [:div.chips {:style style}

       [:div {:style {:flex "0 1 auto"
                      :display "flex"
                      :flex-wrap "wrap"
                      :margin-right "0.5em"}}
        (->> chips
             (map (fn [{:keys [filter-type label]}]
                    [ui/chip {:key label
                              :on-request-delete #(rm-fn label)}
                     label])))]])

(defn _filter-buttons [style]
  [:div.buttons {:style style}
   [ui/flat-button {:background-color "rgb(69, 82, 92)"
                    :icon (r/as-element [ui/svg-icon
                                         [ic/content-add]])
                    :style {:margin "0.25em"
                            :margin-right "0em"}}]
   [ui/flat-button {:background-color "rgb(69, 82, 92)"
                    :icon (r/as-element [ui/svg-icon
                                         [ic/notification-do-not-disturb-alt]])
                    :style {:margin "0.25em"}}]])

(defn _filter-sort [style]
  [:div.sort {:style style}
   [ui/icon-menu {:icon-button-element (r/as-element [ui/icon-button
                                                      [ic/content-sort]])}
    [ui/menu-item {:primary-text "time"}]
    [ui/menu-item {:primary-text "duration"}]
    [ui/menu-item {:primary-text "category"}]]])

(defn filter-comp [filters add-fn clear-fn sort-fn rm-fn]
  (let []

    [ui/paper
     [:div.filter {:style {:display         "flex"
                           :justify-content "space-between"
                           :padding         "0.125em"
                           :max-width       "100%"}}

      (_filter-chips {:display         "flex"
                      :flex            "0 1 auto"
                      :align-items     "flex-start"
                      :justify-content "space-around"}
                     filters
                     rm-fn)

      (_filter-buttons {:display         "flex"
                        :flex            "0 1 auto"
                        :flex-direction  "row"
                        :flex-wrap       "wrap"
                        :justify-content "flex-start"
                        :align-items     "center"})

      (_filter-sort {:display     "flex"
                     :flex        "0 1 auto"
                     :align-items "flex-start"})]]))

