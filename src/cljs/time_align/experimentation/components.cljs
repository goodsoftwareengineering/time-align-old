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

(defn filter-comp [n]
  (let [chips (map (fn [i]
                      {:key (str i)
                       :label (str "thing " i)})
                    (range n))]

    (.log js/console chips)

    [ui/paper
     [:div.filter {:style {:display "flex"
                           :padding "0.125em"
                           :max-width "100%"}}

      [:div.chips {:style {:display "flex"
                           :flex "1 0 70%"
                           :align-items "center"
                           :justify-content "space-around"}}
       [:div {:style {:flex "0 1 auto"
                      :display "flex"
                      :flex-wrap "wrap"
                      :margin-right "0.5em"}}
        (->> chips
             (map (fn [{:keys [key label]}]
                    [ui/chip {:key key
                              :on-request-delete (fn [_] (println (str "remove " label)))}
                     label])))]

       [:div {:style {:flex "1 0 auto"
                      :display "flex"
                      :flex-direction "column"
                      :justify-content "space-between"}}

        [ui/flat-button {:background-color "rgb(69, 82, 92)"
                         :icon (r/as-element [ui/svg-icon
                                              [ic/content-add]])
                         :style {:margin-bottom "0.25em"}}]
        [ui/flat-button {:background-color "rgb(69, 82, 92)"
                         :icon (r/as-element [ui/svg-icon
                                              [ic/notification-do-not-disturb-alt]])}]]]

      [:div.menu {:style {:display "flex"
                          :flex "0 1 auto"
                          :align-items "center"}}
       [ui/icon-menu {:icon-button-element (r/as-element [ui/icon-button
                                                          [ic/content-sort]])}
        [ui/menu-item {:primary-text "time"}]
        [ui/menu-item {:primary-text "duration"}]
        [ui/menu-item {:primary-text "category"}]]]]]))
