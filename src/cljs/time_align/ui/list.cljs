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
(defn list-item-right-menu [menu-item-property-col]
  [ui/icon-menu
   {:list-style {:backgroundColor (:primary-1-color uic/app-theme)}
    :use-layer-for-click-away true
    :icon-button-element
    (r/as-element
     [ui/icon-button
      {:touch true
       :tooltip "more"
       :tooltip-position "bottom-left"}
      [ic/navigation-more-vert {:color (:text-color uic/app-theme)}]])}

   (map (fn [i] [ui/menu-item i]) menu-item-property-col) ])

(defn list-item-period [current-selection period]
  ;; TODO in play period indicator here or in app-bar (tracing dash array?)
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
  (let [{:keys [id name periods complete color category-id description]} task
        sel-id            (:id-or-nil current-selection)
        number-of-periods (count periods)
        is-selected       (= id sel-id)]

    [ui/list-item
     (merge
      {:key           id
       :primaryText   (r/as-element (uic/concatenated-text name "no name entered"))
       :secondaryText   (str "Periods: " number-of-periods)
       :leftIcon      (r/as-element
                        [ui/checkbox {:checked   complete
                                      :on-click
                                      (fn [e]
                                        ;; mui docs say we don't need this
                                        (jsi/stop-propagation e)
                                        ;; but we really do (at least on mobile)
                                        (hist/nav! (str "/edit/task/" id)))
                                      :iconStyle {:fill color}}])
       :style           (if is-selected
                          {:border (str "0.125em solid "
                                        (:border-color uic/app-theme))}
                          {:border (str "0.125em solid "
                                        (:canvas-color uic/app-theme))})
       :onClick         (fn [e]
                          ;; TODO get rid of this or upgrade MUI
                          ;; condition is a dirty nasty hack on the fact that
                          ;; rightIconButton on listItem http://www.material-ui.com/#/components/list
                          ;; doesn't actually stop the click from bubbling up to the item (on desktop only -- mobile is fine)
                          (if (not (clojure.string/includes? (str (type (.-target e))) "SVG"))
                            (hist/nav! (str "/list/periods/" id))))}

      {:rightIconButton (r/as-element
                         (list-item-right-menu
                          [{:key (str id "-edit-menu-item")
                        :primary-text "Edit"
                        :on-click (fn [e]
                                    ;; mui docs say we don't need this
                                    (jsi/stop-propagation e)
                                    ;; but we really do (at least on mobile)
                                    (hist/nav! (str "/edit/task/" id)))
                        :left-icon (r/as-element
                                    [ui/svg-icon
                                     [ic/image-edit
                                      {:color (:text-color uic/app-theme)}]])}
                           {:key (str id "-copy-menu-item")
                        :primary-text "Copy"
                        :on-click (fn [e]
                                    ;; mui docs say we don't need this
                                    (jsi/stop-propagation e)
                                    ;; but we really do (at least on mobile)
                                    (hist/nav! (str "/add/task?name=" name
                                                    "&complete=" complete
                                                    "&category-id=" category-id
                                                    "&description=" description)))
                        :left-icon (r/as-element
                                    [ui/svg-icon
                                     [ic/content-content-copy
                                      {:color (:text-color uic/app-theme)}]])}
                           {:key (str id "-play-menu-item")
                        :primary-text "Play"
                        :on-click (fn [e]
                                    (rf/dispatch [:play-task id])
                                    (hist/nav! "/"))
                        :left-icon (r/as-element
                                    [ui/svg-icon
                                     [ic/av-play-arrow
                                      {:color (:text-color uic/app-theme)}]])}
                           {:key (str id "-bucket-menu-item")
                        :primary-text "Default Play"
                        :on-click (fn [e]
                                    (rf/dispatch [:set-play-bucket id]))
                        :left-icon (r/as-element
                                    [ui/svg-icon
                                     [ic/av-play-circle-outline
                                      {:color (:text-color uic/app-theme)}]])}]))})]))

(defn list-item-category [current-selection category]
  (let [{:keys [id name color tasks]} category
        sel-id                 (:id-or-nil current-selection)
        is-selected            (= id sel-id)
        number-of-tasks        (count tasks)]

    [ui/list-item (merge
                   {:key             id
                    :primaryText     (r/as-element (uic/concatenated-text name "no name entered"))
                    :secondaryText   (str "Tasks: " number-of-tasks)
                    :style           (if is-selected
                                      {:border (str "0.125em solid "
                                                    (:border-color uic/app-theme))}
                                      {:border (str "0.125em solid "
                                                    (:canvas-color uic/app-theme))})
                    :leftIcon        (r/as-element (uic/svg-mui-circle {:color color :style {:margin "0.75em"}}))
                    :onClick         (fn [_]
                                       (hist/nav! (str "/list/tasks/" id)))}

                   {:rightIconButton
                    (r/as-element
                     (list-item-right-menu
                      [{:key (str id "-edit-menu-item")
                        :primary-text "Edit"
                        :on-click (fn [e]
                                    ;; mui docs say we don't need this
                                    (jsi/stop-propagation e)
                                    ;; but we really do (at least on mobile)
                                    (hist/nav! (str "/edit/category/" id)))
                        :left-icon (r/as-element
                                    [ui/svg-icon
                                     [ic/image-edit
                                      {:color (:text-color uic/app-theme)}]])}
                       {:key (str id "-copy-menu-item")
                        :primary-text "Copy"
                        :on-click (fn [e]
                                    ;; mui docs say we don't need this
                                    (jsi/stop-propagation e)
                                    ;; but we really do (at least on mobile)
                                    (hist/nav! (str "/add/category?name=" name
                                                    "&color=" color)))
                        :left-icon (r/as-element
                                    [ui/svg-icon
                                     [ic/content-content-copy
                                      {:color (:text-color uic/app-theme)}]])}]))})]))

(defn breadcrumbs [[root & rest]]
  (let [color (if-let [color (:color root)]
                color
                (:alternate-text-color uic/app-theme))
        link-style {:text-decoration "none"
                    :color (:text-color uic/app-theme)
                    :margin "0.125em"
                    :padding "0.25em"
                    :border-radius "0.25em" ;; TODO animation w/ stylefy
                    :border-bottom (str "0.25em solid " color)
                    :background-color (:primary-1-color uic/app-theme)}
        span-style {:text-decoration "none"
                    :text-decoration-color color
                    :height "100%"}]

    [:div {:style {:padding "1em"
                   :display "flex"
                   :flex-wrap "nowrap"
                   :align-content "stretch"}}

     [:a {:href (:link root) :style link-style}
      [:span {:style span-style}
       (uic/concatenated-text (:label root) "...")]]

     (when (some? rest)
       (->> rest
            (map (fn [r] (when (some? r)
                           [:div {:key (random-uuid)
                                   :style {:display "flex"
                                           :align-items "center"}}
                            [ic/image-navigate-next
                             {:color (:text-color uic/app-theme)}]
                            [:a {:href (:link r) :style link-style}
                             (uic/concatenated-text (:label r) "...")]])))))]))

