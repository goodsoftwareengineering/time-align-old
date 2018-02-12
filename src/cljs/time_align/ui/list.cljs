(ns time-align.ui.list
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.ui.common :as uic]
            [re-frame.core :as rf]
            [time-align.history :as hist]
            [cljs-react-material-ui.icons :as ic]
            [time-align.ui.common :as uic]
            [time-align.client-utilities :as cutils]
            [time-align.utilities :as utils]
            [time-align.js-interop :as jsi]))

(defn list-item-period [current-selection period]
  (let [{:keys [id description color]} period
        sel-id            (:id-or-nil current-selection)
        is-selected       (= id sel-id)]

    (r/as-element
     [ui/list-item
      (merge {:key         id
              :primaryText (uic/period-list-item-primary-text period)
              :secondaryText (uic/period-list-item-secondary-text period)
              :style       (if is-selected {:backgroundColor "#ededed"})
              :on-double-click (fn [e]
                                 (when is-selected
                                   (hist/nav! (str "/edit/period/" id))))}

              (if (and (some? (:start period))
                       (some? (:stop period)))

                ;; if not queue render the arc
                {:leftIcon (r/as-element (uic/mini-arc period))}

                ;; otherwise render a queue indicator
                {:leftIcon (r/as-element
                             [ui/svg-icon [ic/action-list {:color color}]])}))])))

(defn list-item-task [current-selection task]
  (let [{:keys [id name periods complete color]} task
        sel-id            (:id-or-nil current-selection)
        number-of-periods (count periods)
        is-selected       (= id sel-id)]
    [ui/list-item
     {:key           id
      :primaryText   (uic/concatenated-text name 15 "no name entered ...")
      :secondaryText   (str "Periods: " number-of-periods)
      :leftIcon      (r/as-element
                      [ui/checkbox {:checked   complete
                                    :iconStyle {:fill color}}])
      :style         (if is-selected {:backgroundColor "#ededed"})
      :onClick       (fn [e]
                       (hist/nav! (str "/list/periods/" id)))
      :rightIconButton (r/as-element [ui/icon-button {:onClick (fn [e]
                                                                 ;; mui docs say we don't need this
                                                                 (jsi/stop-propagation e)
                                                                 ;; but we really do (at least on mobile)
                                                                 (hist/nav! (str "/edit/task/" id)))}
                                      [ic/image-edit
                                       {:color (:secondary uic/app-theme)}]])}]))

(defn list-item-category [current-selection category]
  (let [{:keys [id name color tasks]} category
        sel-id                 (:id-or-nil current-selection)
        is-selected            (= id sel-id)
        number-of-tasks        (count tasks)]

    [ui/list-item {:key             id
                   :primaryText     (uic/concatenated-text name 20
                                                           "no name entered ...")
                   :secondaryText   (str "Tasks: " number-of-tasks)
                   :leftIcon        (r/as-element (uic/svg-mui-circle color))
                   :style           (if is-selected {:backgroundColor "#ededed"})
                   :onClick         (fn [e]
                                      (when-not is-selected
                                        (hist/nav! (str "/list/tasks/" id))))
                   :rightIconButton (r/as-element [ui/icon-button {:onClick (fn [e]
                                                                              ;; mui docs say we don't need this
                                                                              (jsi/stop-propagation e)
                                                                              ;; but we really do (at least on mobile)
                                                                              (hist/nav! (str "/edit/category/" id)))}
                                                   [ic/image-edit
                                                    {:color (:secondary uic/app-theme)}]])}]))


;;  [ui/raised-button {:key (str "add-period-for-task-" id)
;;                     :href "#/add/period"
;;                     :label "Add Period"
;;                     :background-color "grey"
;;                     :style {:margin-top "1em"
;;                             :margin-left "3em"
;;                             :margin-bottom "1em"}}]
