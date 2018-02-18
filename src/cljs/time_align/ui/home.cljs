(ns time-align.ui.home
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [time-align.ui.svg-day-view :as day-view]
            [time-align.ui.app-bar :as ab :refer [app-bar]]
            [time-align.ui.queue :as qp]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [time-align.ui.calendar :as cp]
            [time-align.ui.common :as uic]
            [time-align.client-utilities :as cutils]
            [time-align.ui.action-buttons :as actb]
            [time-align.ui.agenda :as ap]
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

    [ui/icon-button {:onClick (fn [e] (zoom-fn zoom-arg))
                     :style (if invert
                              {:background-color (:accent2-color uic/app-theme)}
                              {})}
     [ui/svg-icon
      {:viewBox "0 0 50 50" :style {:width "100%" :height "100%"}}
      [:circle {:cx 25 :cy 25 :r 22
                :fill (if invert (:accent2-color uic/app-theme)
                          (:canvas-color uic/app-theme))
                :stroke (if invert (:primary1-color uic/app-theme)
                            (:alternate-text-color uic/app-theme))
                :stroke-width "5"}]
      [:path (merge {:fill (if invert (:primary1-color uic/app-theme)
                               (:alternate-text-color uic/app-theme))}
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
  (let [tasks                @(rf/subscribe [:tasks])
        selected             @(rf/subscribe [:selected])
        action-button-state  @(rf/subscribe [:action-buttons])
        displayed-day        @(rf/subscribe [:displayed-day])
        periods              @(rf/subscribe [:periods])
        period-in-play       @(rf/subscribe [:period-in-play])
        ;; dashboard-tab       @(rf/subscribe [:dashboard-tab])
        zoom                 @(rf/subscribe [:zoom])
        inline-period-dialog @(rf/subscribe [:inline-period-add-dialog])]

    [:div.app-container
     {:style {:display         "flex"
              :flex-wrap       "wrap"
              :justify-content "center"
              :align-content   "flex-start"
              :height          "100%"
              ;; :border "yellow solid 0.1em"
              :box-sizing      "border-box"}}

     (app-bar)

     [ui/paper {:style (merge {:width "100%"}
                              (if (nil? zoom)
                                {:margin-bottom "3em"}
                                {:margin-bottom "0.05em"}))}
      [:div.navigation.zoom
       {:style {:display         "flex"
                :justify-content "space-between"
                :flex-wrap       "nowrap"}}

       [ui/icon-button
        {:onClick (fn [e]
                    (rf/dispatch [:iterate-displayed-day :prev]))}
        [ic/image-navigate-before {:color (:alternate-text-color uic/app-theme)}]]

       (svg-mui-zoom 1)
       (svg-mui-zoom 4)
       [ui/icon-button
        {:onClick (fn [e] (rf/dispatch [:set-displayed-day (new js/Date)]))}
        (if (cutils/same-day? displayed-day (new js/Date))
          [ic/device-gps-fixed {:color (:alternate-text-color uic/app-theme)}]
          [ic/device-gps-not-fixed {:color (:alternate-text-color uic/app-theme)}])]
       (svg-mui-zoom 3)
       (svg-mui-zoom 2)

       [ui/icon-button
        {:onClick (fn [e]
                    (rf/dispatch [:iterate-displayed-day :next]))}
        [ic/image-navigate-next {:color (:alternate-text-color uic/app-theme)}]]]]

     [:div.day-container
      {:style (merge
               {:display         "flex"
                :flex            "1 0 100%"
                :box-sizing      "border-box"}
               (when (some? zoom)
                 {:height          "100%"}))}

      (day-view/day tasks selected displayed-day)]

     [:div.action-container
      {:style {:position   "fixed"
               :right      "0"
               :bottom     "0"
               :z-index    "99"
               :padding    "0.75em"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      (actb/action-buttons action-button-state selected period-in-play)]]))
