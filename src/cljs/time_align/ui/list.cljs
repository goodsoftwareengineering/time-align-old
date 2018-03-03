(ns time-align.ui.list
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.ui.common :as uic]
            [re-frame.core :as rf]
            [time-align.history :as hist]
            [time-align.utilities :as utils]
            [cljs-react-material-ui.icons :as ic]
            [time-align.ui.common :as uic]
            [time-align.client-utilities :as cutils]
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
              :on-click        (if is-selected
                                 (fn [_] (rf/dispatch [:set-selected-period nil]))
                                 (fn [_] (rf/dispatch [:set-selected-period id])))}

              (if (cutils/period-has-stamps period)

                ;; if not queue render the arc
                {:leftIcon (r/as-element (uic/mini-arc period))}

                ;; otherwise render a queue indicator
                {:leftIcon (r/as-element
                            [ui/svg-icon [ic/action-list {:color color}]])})

              ;; {:rightIconButton (r/as-element (list-item-right-menu)) ;; example of using menu
              (when (cutils/period-has-stamps period)
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
                     :style {:transform "scale(-1,1)"}}]])}
                ))]))

(defn list-item-task [current-selection task]
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
       :style           (if is-selected
                          {:border (str "0.125em solid "
                                        (:border-color uic/app-theme))}
                          {:border (str "0.125em solid "
                                        (:canvas-color uic/app-theme))})
       :onClick         (fn [_] (hist/nav! (str "/list/periods/" id)))}

      {:rightIconButton (r/as-element
                         [ui/icon-button
                          {:onClick
                           (fn [e]
                             ;; mui docs say we don't need this
                             (jsi/stop-propagation e)
                             ;; but we really do (at least on mobile)
                             (hist/nav! (str "/edit/task/" id)))}
                          [ic/image-edit
                           {:color (:primary uic/app-theme)}]])})]))

(defn list-item-category [current-selection category]
  (let [{:keys [id name color tasks]} category
        sel-id                 (:id-or-nil current-selection)
        is-selected            (= id sel-id)
        number-of-tasks        (count tasks)]

    [ui/list-item (merge
                   {:key             id
                   :primaryText     (uic/concatenated-text name 20
                                                           "no name entered ...")
                   :secondaryText   (str "Tasks: " number-of-tasks)
                   :style           (if is-selected
                                      {:border (str "0.125em solid "
                                                    (:border-color uic/app-theme))}
                                      {:border (str "0.125em solid "
                                                    (:canvas-color uic/app-theme))})
                   :leftIcon        (r/as-element (uic/svg-mui-circle color))
                    :onClick         (fn [_]
                                       (hist/nav! (str "/list/tasks/" id)))}

                   {:rightIconButton
                    (r/as-element
                     [ui/icon-button
                      {:onClick
                       (fn [e]
                         ;; mui docs say we don't need this
                         (jsi/stop-propagation e)
                         ;; but we really do (at least on mobile)
                         (hist/nav! (str "/edit/category/" id)))}
                      [ic/image-edit
                       {:color (:primary uic/app-theme)}]])})]))

(defn breadcrumbs [[root & rest]]
  (let [color (if-let [color (:color root)]
                color
                (:alternate-text-color uic/app-theme))
        link-style {:text-decoration "none"
                    :color (:text-color uic/app-theme)
                    :margin "0.125em"
                    :padding "0.25em"
                    :border-radius "0.25em"
                    :border (str "0.125em solid " color)
                    :background-color (:primary-2-color uic/app-theme)}
        span-style {:text-decoration "none"
                    :text-decoration-color color}]

    [:div {:style {:padding "1em"
                   :display "flex"
                   :flex-wrap "nowrap"}}

     [:a {:href (:link root) :style link-style}
      [:span {:style span-style}
       (uic/concatenated-text (:label root) 10 "...")]]

     (when (some? rest)
       (->> rest
            (map (fn [r] (when (some? r)
                           [:span {:style {:display "flex"
                                           :align-items "center"}}
                            [ic/image-navigate-next
                             {:color (:text-color uic/app-theme)}]
                            [:a {:href (:link r) :style link-style}
                             [:span {:style span-style}
                              (uic/concatenated-text (:label r) 8 "...")]]])))))]))
