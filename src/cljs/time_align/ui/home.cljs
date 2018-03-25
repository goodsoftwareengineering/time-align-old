(ns time-align.ui.home
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [re-learn.core :as re-learn]
            [time-align.ui.svg-day-view :as day-view]
            [time-align.ui.app-bar :as ab :refer [app-bar menu]]
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
            [stylefy.core :as stylefy]
            [time-align.js-interop :as jsi]))

(defn svg-mui-zoom
  "cartesian quadrants go counter clockwise"
  [quadrant]
  (let [zoom @(rf/subscribe [:zoom])
        d (case quadrant
            1 {:d "M25,25 L25,0  A25,25 0 0 1 47,25 z"}
            2 {:d "M25,25 L25,0  A25,25 0 0 0 3,25 z"}
            3 {:d "M25,25 L25,47 A25,25 0 0 1 3,25 z"}
            4 {:d "M25,25 L25,47 A25,25 0 0 0 47,25 z"})

        zoom-fn #(rf/dispatch [:set-zoom %])
        za (case quadrant
              1 :q1
              2 :q2
              3 :q3
              4 :q4)
        invert (= zoom za)
        zoom-arg (if (and (some? zoom) invert) nil za)]

    [ui/icon-button {:onClick (fn [e] (zoom-fn zoom-arg))}
     [ui/svg-icon
      {:viewBox "0 0 50 50" :style {:width "100%" :height "100%"}}
      [:circle {:cx 25 :cy 25 :r 22
                :fill "rgba(0,0,0,0)"
                :stroke (if invert (:text-color uic/app-theme)
                            (:canvas-color uic/app-theme))
                :stroke-width "5"}]
      [:path (merge {:fill (if invert (:text-color uic/app-theme)
                               (:canvas-color uic/app-theme))}
                    d)]]]))

(defn navigation-zoom [displayed-day]
  [ui/paper {:style (merge {:width "100%"})}
      [:div.navigation.zoom
       {:style {:display         "flex"
                :justify-content "space-between"
                :align-items     "center"
                :flex-wrap       "nowrap"}}

       [ui/icon-button
        {:onClick (fn [e]
                    (rf/dispatch [:iterate-displayed-day :prev]))}
        [ic/image-navigate-before {:color (:alternate-text-color uic/app-theme)}]]

       [:div {:onClick (fn [e] (rf/dispatch [:set-displayed-day (new js/Date)]))}
        [:span {:style (if (cutils/same-day? displayed-day (new js/Date))
                         {:border (str "0.125em solid " (:text-color uic/app-theme))
                          :border-radius "0.125em"
                          :padding "0.25em"})}
         (jsi/->date-string displayed-day)]]

       [ui/icon-button
        {:onClick (fn [e]
                    (rf/dispatch [:iterate-displayed-day :next]))}
        [ic/image-navigate-next {:color (:alternate-text-color uic/app-theme)}]]]])

(def period-info-tree-expansion (r/atom :period))

(defn period-info-tree [selected-period tasks categories]
  (let [expand @period-info-tree-expansion
        task  (cutils/find-task-with-period tasks (:id selected-period))
        category (cutils/find-category-with-task categories (:id task))
        color (:color category)
        complete (:complete task)
        description (:description selected-period)
        line-style {:display "flex"
                    :width "100%"
                    :justify-content "flex-start"}]

    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :justify-content "space-between"}}

     [:div {:style line-style}
      (uic/svg-mui-circle {:color color :style {:width "auto"}})
       [:div {:style {:margin-right "0.25em"}}]
       (uic/concatenated-text (:name category) "...")]

     [:div {:style line-style}
      [ui/checkbox {:checked  complete
                    :iconStyle {:fill color
                                :margin "0"}
                    :style {:width "auto"}}]
       [:div {:style {:margin-right "0.25em"}}]
       (uic/concatenated-text (:name task) "...")]

     [:div {:style line-style}
      [:div {:style {}}
       (if (cutils/period-has-stamps selected-period)
         (uic/mini-arc selected-period)
         [ui/svg-icon [ic/action-list {:color color}]])]
       [:div {:style {:margin-right "0.25em"}}]
       (uic/concatenated-text description "no description")]]))

(defn period-info-time [selected-period]
  (let [has-stamps (cutils/period-has-stamps selected-period)
        start      (if has-stamps
                     (jsi/->locale-time-string (:start selected-period)))
        stop       (if has-stamps
                     (jsi/->locale-time-string (:stop selected-period)))
        total-ms   (if has-stamps
                     (- (jsi/value-of (:stop selected-period))
                        (jsi/value-of (:start selected-period))))
        total-h    (if has-stamps
                     (jsi/round-decimals
                      (/ total-ms utils/hour-ms)
                      2))]

    [:div {:style {:display         "flex"
                   :flex-direction  "row"
                   :justify-content "space-between"
                   :align-items     "flex-end"
                   :color           (:alternate-text-color uic/app-theme)}}
     [:span start]
     [:span stop]
     [:span (if has-stamps (str total-h " hours")
                "---")]]))

(defn period-info [selected-period categories tasks]
  (let [now (:time @uic/clock-state)]
    [ui/paper {:style {:width "100%"
                       :padding "0.25em"}}
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :justify-content "center"
                    :height "5.25em"}}

      (if (some? selected-period)
        [:div {:on-click (fn [_]
                           (if (cutils/period-has-stamps selected-period)
                             (do (rf/dispatch [:set-displayed-day
                                               (:start selected-period)])
                                 (hist/nav! "/"))
                             (hist/nav! "/queue")))}
         (period-info-tree selected-period tasks categories)
         (period-info-time selected-period)]

        [:div {:style {:align-self "center"}}
         [:h1 {:style {:color (:alternate-text-color uic/app-theme)}}
          (jsi/->time-string-relaxed now)]])]]))

(defn action-buttons [period-in-play selected-id selected-period]
  (if (and (some? period-in-play)
           (nil? selected-id))
    (actb/action-buttons-pause period-in-play)

    ;; playing    selection
    ;; no playing selection
    (if (some? selected-id)
      (actb/action-buttons-period-selection
       period-in-play
       selected-id
       (fn [_]
         (let [{:keys [description start stop task-id]} selected-period]
           (hist/nav! (str "#/add/period?"
                           (when (cutils/period-has-stamps selected-period)
                             (str "start-time=" (.valueOf start)
                                  "&stop-time=" (.valueOf stop)))
                           "&description=" description
                           "&task-id=" task-id))))))))

(defn home-page-comp []
  (let [tasks                @(rf/subscribe [:tasks])
        selected             @(rf/subscribe [:selected])
        current-selection    (:current-selection selected)
        selected-id          (:id-or-nil current-selection)
        action-button-state  @(rf/subscribe [:action-buttons])
        displayed-day        @(rf/subscribe [:displayed-day])
        periods              @(rf/subscribe [:periods])
        categories           @(rf/subscribe [:categories])
        selected-period      (some #(if (= selected-id (:id %)) %) periods)
        period-in-play       @(rf/subscribe [:period-in-play])
        in-play-period       (some #(if (= period-in-play (:id %)) %) periods)
        zoom                 @(rf/subscribe [:zoom])
        inline-period-dialog @(rf/subscribe [:inline-period-add-dialog])
        day-display-container-style (merge
                                     {:display         "flex"
                                      :flex            "0 0 100%"
                                      :box-sizing      "border-box"
                                      :position        "relative"}
                                     (when (some? zoom)
                                       {:height          "100%"})
                                     (when (nil? zoom)
                                       {::stylefy/media {{:min-width "900px"}
                                                         {:flex "0 0 50%"
                                                          :height "70%"}}}))]

    [:div.app-container
     {:style {:display         "flex"
              :flex-wrap       "wrap"
              :justify-content "center"
              :align-content   "flex-start"
              :height          "100%"
              ;; :border "yellow solid 0.1em"
              :box-sizing      "border-box"}}

     (app-bar)

     (navigation-zoom displayed-day)

     [ui/divider {:style {:margin "0.125em"}}]

     (when (nil? zoom)
       (cond (some? selected-period)
             (period-info selected-period categories tasks)

             (some? in-play-period)
             (period-info in-play-period categories tasks)

             :else
             (period-info nil categories tasks)))

     [:div.day-container
      (stylefy/use-style day-display-container-style)

      (when (or (nil? zoom)
                (= zoom :q1))
        [:div.zoom-one {:style {:position "absolute" :top "0" :right "0"}}
         (svg-mui-zoom 1)])

      (when (or (nil? zoom)
                (= zoom :q2))
        [:div.zoom-one {:style {:position "absolute" :top "0" :left "0"}}
         (svg-mui-zoom 2)])

      (when (or (nil? zoom)
                (= zoom :q3))
        [:div.zoom-one {:style {:position (if (= zoom :q3) "fixed" "absolute")
                                :bottom (if (= zoom :q3) "70px" "16px") :left "0"}}
         (svg-mui-zoom 3)])

      (when (or (nil? zoom)
                (= zoom :q4))
        [:div.zoom-one {:style {:position (if (= zoom :q4) "fixed" "absolute")
                                :bottom (if (= zoom :q4) "70px" "16px") :right "0"}}
         (svg-mui-zoom 4)])

      [day-view/day tasks selected displayed-day]]

     [:div.action-container
      {:style {:position   "fixed"
               :right      "0"
               :bottom     "0"
               :z-index    "99"
               :padding    "0.75em"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}

      (action-buttons period-in-play selected-id selected-period)]]))

(def home-page
  (re-learn/with-lesson
    {:id :home-page-lesson
     :description "This is the home page. It displays a day with two tracks -- planned and actual."
     :position :unattached}

    home-page-comp))
