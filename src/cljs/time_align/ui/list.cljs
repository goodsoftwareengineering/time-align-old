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

;; Kept this for example of menu
;; (defn list-item-right-menu []
;;   [ui/icon-menu
;;    {:list-style {:backgroundColor (:primary-1-color uic/app-theme)}
;;     :icon-button-element
;;     (r/as-element
;;      [ui/icon-button
;;       {:touch true
;;        :tooltip "more"
;;        :tooltip-position "bottom-left"}
;;       [ic/navigation-more-vert {:color (:text-color uic/app-theme)}]])}

;;    [ui/menu-item "Jump To"]
;;    [ui/menu-item "Edit"]])

(defn list-item-period [current-selection period]
  (let [{:keys [id description color]} period
        sel-id                         (:id-or-nil current-selection)
        is-selected                    (= id sel-id)]

     [ui/list-item
      (merge {:key             id
              :primaryText     (uic/period-list-item-primary-text period)
              :secondaryText   (uic/period-list-item-secondary-text period)
              :style           (if is-selected {:border (str "0.125em solid "
                                                             (:border-color uic/app-theme))}
                                   {:border (str "0.125em solid "
                                                 (:canvas-color uic/app-theme))})
              :on-click        (if (= sel-id id)
                                 (fn [_] (rf/dispatch [:set-selected-period nil]))
                                 (fn [_] (rf/dispatch [:set-selected-period id])))}

              (if (and (some? (:start period))
                       (some? (:stop period)))

                ;; if not queue render the arc
                {:leftIcon (r/as-element (uic/mini-arc period))}

                ;; otherwise render a queue indicator
                {:leftIcon (r/as-element
                            [ui/svg-icon [ic/action-list {:color color}]])})

              ;; {:rightIconButton (r/as-element (list-item-right-menu)) ;; example of using menu
               {:rightIconButton
               (r/as-element
                [ui/icon-button
                 {:onClick
                  (fn [e]
                    (jsi/stop-propagation e)
                    (rf/dispatch [:set-displayed-day (:start period)])
                    (hist/nav! "/"))}
                 [ic/content-reply
                  {:color (:primary uic/app-theme)
                   :style {:transform "scale(-1,1)"}}]])})]))

(defn list-item-task [current-selection not-parent task]
  (let [{:keys [id name periods complete color]} task
        sel-id            (:id-or-nil current-selection)
        number-of-periods (count periods)
        is-selected       (= id sel-id)]

    [ui/list-item
     (merge
      {:key           id
       :primaryText   (uic/concatenated-text name 15 "no name entered ...")
       :secondaryText   (str "Periods: " number-of-periods)
       :leftIcon      (r/as-element
                       [ui/checkbox {:checked   complete
                                     :iconStyle {:fill color}}])
       :onClick       (fn [e]
                        (if not-parent
                          (hist/nav! (str "/list/periods/" id))
                          (hist/nav! (str "/list/tasks/" (:category-id task)))))}

      {:rightIconButton (r/as-element
                         [ui/icon-button
                          {:onClick
                           (fn [e]
                             ;; mui docs say we don't need this
                             (jsi/stop-propagation e)
                             ;; but we really do (at least on mobile)
                             (hist/nav! (str "/edit/task/" id)))}
                          [ic/editor-mode-edit
                           {:color (:primary uic/app-theme)}]])})]))

(defn list-item-category [current-selection not-parent category]
  (let [{:keys [id name color tasks]} category
        sel-id                 (:id-or-nil current-selection)
        is-selected            (= id sel-id)
        number-of-tasks        (count tasks)]

    [ui/list-item (merge
                   {:key             id
                   :primaryText     (uic/concatenated-text name 20
                                                           "no name entered ...")
                   :secondaryText   (str "Tasks: " number-of-tasks)
                   :leftIcon        (r/as-element (uic/svg-mui-circle color))
                   :onClick         (fn [e]
                                      (if not-parent
                                        (hist/nav! (str "/list/tasks/" id))
                                        (hist/nav! "/list/categories")))}

                   {:rightIconButton (r/as-element [ui/icon-button
                                                    {:onClick
                                                     (fn [e]
                                                       ;; mui docs say we don't need this
                                                       (jsi/stop-propagation e)
                                                       ;; but we really do (at least on mobile)
                                                       (hist/nav! (str "/edit/category/" id)))}
                                                    [ic/editor-mode-edit
                                                     {:color (:primary uic/app-theme)}]])})]))
