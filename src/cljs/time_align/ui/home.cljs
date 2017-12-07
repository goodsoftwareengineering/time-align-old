(ns time-align.ui.home
  (:require [re-frame.core :as rf]
            [time-align.ui.svg-day-view :as day-view]
            [time-align.ui.app-bar :as ab :refer [app-bar]]
            [time-align.ui.queue :as qp]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.ui.common :as uic]
            [time-align.client-utilities :as cutils]
            [time-align.ui.action-buttons :as actb]
            [time-align.ui.agenda :as ap]
            [time-align.js-interop :as jsi]))

(defn svg-mui-zoom
  "cartesian quadrants go counter clockwise"
  [quadrant]
  (let [
        zoom @(rf/subscribe [:zoom])
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
        zoom-arg (if (and (some? zoom) invert) nil za)
        ]

    [ui/icon-button {:onClick (fn [e] (zoom-fn zoom-arg))
                     :style (if invert
                              {:background-color "grey"}
                              {})}
     [ui/svg-icon
      {:viewBox "0 0 50 50" :style {:width "100%" :height "100%"}}
      [:circle {:cx 25 :cy 25 :r 22
                :fill (if invert "grey" "white")
                :stroke (if invert "white" "grey")
                :stroke-width "4"}]
      [:path (merge {:fill (if invert "white" "grey")}
                    d)]]]))

(defn stats-selection [selected periods tasks]
  (let [id (->> selected
                (:current-selection)
                (:id-or-nil))
        period (some #(if (= id (:id %)) %) periods)
        task-id (:task-id period)
        task (some #(if (= task-id (:id %)) %) tasks)
        ]
    [:div
     [ui/table {:selectable false}
      [ui/table-body {:display-row-checkbox false}
       [ui/table-row
        [ui/table-row-column "task"]
        [ui/table-row-column (:name task)]
        ]
       [ui/table-row
        [ui/table-row-column "start"]
        [ui/table-row-column (jsi/->time-string (:start period))]
        ]
       [ui/table-row
        [ui/table-row-column "stop"]
        [ui/table-row-column (jsi/->time-string (:stop period))]
        ]
       ]
      ]
     [:p {:style {:padding "0.25em"}} (:description period)]
     ]
    ))

(defn stats-no-selection []
  (let [planned-time @(rf/subscribe [:planned-time :selected-day])
        accounted-time @(rf/subscribe [:accounted-time :selected-day])
        tasks @(rf/subscribe [:tasks])
        queue-items (cutils/filter-periods-no-stamps tasks)
        queue-count (count queue-items)
        incomplete-tasks (filter #(:complete %) tasks)
        incomplete-count (count incomplete-tasks)
        ]

    [ui/table {:selectable false}
     [ui/table-body {:display-row-checkbox false}
      [ui/table-row
       [ui/table-row-column "planned"]
       [ui/table-row-column (uic/duration-ms-to-string planned-time)]
       ]
      [ui/table-row
       [ui/table-row-column "accounted"]
       [ui/table-row-column (uic/duration-ms-to-string accounted-time)]
       ]
      [ui/table-row
       [ui/table-row-column "queue items"]
       [ui/table-row-column queue-count]
       ]
      [ui/table-row
       [ui/table-row-column "incomplete tasks"]
       [ui/table-row-column incomplete-count]
       ]
      ]
     ]
    )
  )

(defn home-page []
  (let [
        tasks               @(rf/subscribe [:tasks])
        selected            @(rf/subscribe [:selected])
        action-button-state @(rf/subscribe [:action-buttons])
        displayed-day       @(rf/subscribe [:displayed-day])
        periods             @(rf/subscribe [:periods])
        period-in-play      @(rf/subscribe [:period-in-play])
        dashboard-tab       @(rf/subscribe [:dashboard-tab])
        inline-period-dialog @(rf/subscribe [:inline-period-add-dialog])]

    [:div.app-container
     {:style {:display         "flex"
              :flex-wrap       "wrap"
              :justify-content "center"
              :align-content   "space-between"
              :height          "100%"
              ;; :border "yellow solid 0.1em"
              :box-sizing      "border-box"}}

     (app-bar)

     [:div.day-container
      {:style {:display    "flex"
               :flex       "1 0 100%"
               :max-height "60%"
               ;; :border "red solid 0.1em"
               :box-sizing "border-box"
               :justify-content "center"
               :align-items "center"
               :flex-direction "column"}}

      (day-view/day tasks selected displayed-day)]

     [:div.lower-container
      {:style {:display    "flex"
               :flex       "1 0 100%"
               ;; :border "blue solid 0.1em"
               :box-sizing "border-box"}}
      [ui/paper {:style {:width      "100%"
                         :min-height "10em" ;; keeps the tabs above action
                                            ;; and from tabs 'jumping' if there
                                            ;; is no content in other tab
                                            ;; at least on mobile
                                            ;; TODO add breakpoint rules
                         }}

       [:div.day-label {:style {:color "grey" :padding "0.01em" :text-align "center"}}
        [:span (jsi/->date-string displayed-day)]]

       [ui/divider {:style {:margin-top    "0"
                            :margin-bottom "0"}}]

       [:div.navigation.zoom
        {:style {:display "flex"
                 :justify-content "space-between"}}
        [ui/icon-button
         {:onClick (fn [e]
                     (rf/dispatch [:iterate-displayed-day :prev]))}
         [ui/svg-icon
          {:viewBox "0 0 50 50" :style {:width "100%" :height "100%"}}
          [:polyline {:points       "25,0 0,25 25,50"
                      :fill         "grey"
                      :fill-opacity "1"
                      }]]]

        (svg-mui-zoom 1)
        (svg-mui-zoom 4)
        (svg-mui-zoom 3)
        (svg-mui-zoom 2)

        [ui/icon-button
         {:onClick (fn [e]
                     (rf/dispatch [:iterate-displayed-day :next]))}
         [ui/svg-icon
          {:viewBox "0 0 50 50" :style {:width "100%" :height "100%"}}
          [:polyline {:points       "50,25 25,0 25,50"
                      :fill         "grey"
                      :fill-opacity "1"
                      }]]]]

       [ui/divider {:style {:margin-top    "0"
                            :margin-bottom "0"}}]

       [ui/tabs {:tabItemContainerStyle {:backgroundColor "white"}
                 :inkBarStyle           {:backgroundColor (:primary uic/app-theme)}
                 :value                 (name dashboard-tab)
                 :on-change             (fn [v]
                                           (rf/dispatch [:set-dashboard-tab (keyword v)]))}

        [ui/tab {:label "agenda" :style {:color (:primary uic/app-theme)}
                 :value "agenda"}
         (ap/agenda selected periods)
         ]
        [ui/tab {:label "queue" :style {:color (:primary uic/app-theme)}
                 :value "queue"}
         (qp/queue tasks selected)
         ]
        [ui/tab {:label "stats" :style {:color (:primary uic/app-theme)}
                 :value "stats"}
         (if (= :period (get-in selected [:current-selection :type-or-nil]))
           (stats-selection selected periods tasks)
           (stats-no-selection)
           )
         ]
        ]
       ]
      ]

     [:div.action-container
      {:style {:position   "fixed"
               :right      "0"
               :bottom     "0"
               :z-index    "99"
               :padding    "0.75em"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      (actb/action-buttons action-button-state selected period-in-play)]]))
