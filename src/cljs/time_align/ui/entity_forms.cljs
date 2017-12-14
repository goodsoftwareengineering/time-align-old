(ns time-align.ui.entity-forms
  (:require [re-frame.core :as rf]
            [time-align.client-utilities :as cutils]
            [cljs-react-material-ui.reagent :as ui]
            [reagent.core :as r]
            [cljs-react-material-ui.icons :as ic]
            [cljs-react-material-ui.core :refer [color]]
            [time-align.ui.common :as uic]
            [time-align.js-interop :as jsi]))

(defn svg-mui-task-symbol [{:keys [color style]}]
  [ui/svg-icon
   (merge {:style style} {:viewBox "0 0 24 24"})
   [:g

    [:polyline {:points "12,1 1,12 12,23 23,12 12,1"
                :fill   color}]
    ]
   ]
  )


(def standard-colors (->> (aget js/MaterialUIStyles "colors")
                          (js->clj)
                          (keys)
                          (filter (fn [c] (some? (re-find #"500" c))))
                          (map (fn [c] (color (keyword c))))
                          ))

(defn standard-color-picker []
  [:div.colors {:style {:display         "flex"
                        :flex-wrap       "wrap"
                        :justify-content "center"
                        :marginTop       "1em"}}
   (->> standard-colors
        (map (fn [c]
               [:div.color {:key     c
                            :style   {:width           "2em"
                                      :height          "2em"
                                      :backgroundColor c}
                            :onClick (fn [e]
                                       (rf/dispatch [:set-category-form-color
                                                     (cutils/color-hex->255 c)]))}]))
        )
   ])

(defn color-slider [color]
  [:div.slider
   [ui/slider {:value    (:red color)
               :min      0
               :max      255
               :onChange (fn [e v]
                           (rf/dispatch [:set-category-form-color
                                         {:red (Math/ceil v)}]))}]
   [ui/slider {:value    (:green color)
               :min      0
               :max      255
               :onChange (fn [e v]
                           (rf/dispatch [:set-category-form-color
                                         {:green (Math/ceil v)}]))}]
   [ui/slider {:value    (:blue color)
               :min      0
               :max      255
               :onChange (fn [e v]
                           (rf/dispatch [:set-category-form-color
                                         {:blue (Math/ceil v)}]))}]
   ]
  )

(defn entity-form-buttons [save-dispatch-vec delete-dispatch-vec]
  [:div.buttons {:style {:display         "flex"
                         :justify-content "space-between"
                         :margin-top      "1em"
                         }}

   [ui/flat-button {:icon            (r/as-element [ic/action-delete-forever uic/basic-ic])
                    :backgroundColor (:secondary uic/app-theme)
                    :onTouchTap      (fn [e]
                                       (rf/dispatch delete-dispatch-vec)
                                       )}]

   [ui/flat-button {:icon            (r/as-element [ic/navigation-cancel uic/basic-ic])
                    :backgroundColor "grey"
                    :onTouchTap      (fn [e]
                                       (jsi/back! js/history)
                                       )}]
   [ui/flat-button {:icon            (r/as-element [ic/content-save uic/basic-ic])
                    :backgroundColor (:primary uic/app-theme)
                    :onTouchTap      (fn [e]
                                       (rf/dispatch save-dispatch-vec)
                                       )}]
   ])

(defn category-form [id]
  (let [color @(rf/subscribe [:category-form-color])
        name  @(rf/subscribe [:category-form-name])]

    [:div
     [ui/text-field {:floating-label-text "Name"
                     :default-value       name
                     :onChange            (fn [e v]
                                            (rf/dispatch-sync [:set-category-form-name v]))}]

     [:div.colorHeader {:style {:display         "flex"
                                :flexWrap        "nowrap"
                                :align-items     "center"
                                :justify-content "space-around"}}
      [ui/svg-icon {:viewBox "0 0 1000 1000" :style {:margin-left "0.5em"}}
       [:circle {:cx "500" :cy "500" :r "500" :fill (cutils/color-255->hex color)}]]
      [ui/subheader "Color"]]

     [ui/tabs {:tabItemContainerStyle {:backgroundColor "white"}
               :inkBarStyle           {:backgroundColor (:primary uic/app-theme)}}
      [ui/tab {:label "picker" :style {:color (:primary uic/app-theme)}}
       (standard-color-picker)]
      [ui/tab {:label "slider" :style {:color (:primary uic/app-theme)}}
       (color-slider color)]]

     [ui/divider {:style {:margin-top    "1em"
                          :margin-bottom "1em"}}]

     (entity-form-buttons [:save-category-form] [:delete-category-form-entity])]

    )
  )

(defn category-menu-item [category]
  (let [id (str (:id category))]
    [ui/menu-item
     {:key         id
      :value       id
      :primaryText (:name category)
      :leftIcon    (r/as-element
                     (uic/svg-mui-circle (:color category)))}]
    )
  )

(defn category-selection-render [categories id]
  (->> categories
       (some #(if (= (:id %) (uuid id)) %))
       (category-menu-item)
       (r/as-element)
       ))

(defn task-menu-item [task]
  (let [id (str (:id task))]
    [ui/menu-item
     {:key         (str id)
      :value       (str id)
      :primaryText (:name task)
      :leftIcon    (r/as-element
                     (uic/svg-mui-circle (:color task)))}]
    )
  )

(defn task-selection-render [tasks id]
  (->> tasks
       (some #(if (= (:id %) (uuid id)) %))
       (task-menu-item)
       (r/as-element)
       )
  )

(defn task-form [id]
  (let [name        @(rf/subscribe [:task-form-name])
        description @(rf/subscribe [:task-form-description])
        complete    @(rf/subscribe [:task-form-complete])
        category-id @(rf/subscribe [:task-form-category-id])
        categories  @(rf/subscribe [:categories])
        ]

    [:div
     [ui/text-field {:floating-label-text "Name"
                     :fullWidth           true
                     :default-value       name
                     :onChange            (fn [e v]
                                 (rf/dispatch-sync [:set-task-form-name v])
                                 )}]

     [ui/text-field {:floating-label-text "Description"
                     :fullWidth           true
                     :multiLine           true
                     :rows                4
                     :default-value       description
                     :onChange            (fn [e v]
                                 (rf/dispatch-sync [:set-task-form-description v])
                                 )}]

     [ui/select-field
      ;; select fields get a little strange with the parent entity id's
      ;; this goes for tasks and periods
      ;; the value stored on the mui element is a string conversion of the uuid
      ;; the value stored in the app-db form is of uuid type
      ;; function for the renderer, in this select field element, takes in a string id
      ;; but converts it to uuid type before comparing to the collection
      {:value             (str category-id)
       :floatingLabelText "Category"
       :autoWidth         true
       :fullWidth         true
       :selectionRenderer (partial
                            category-selection-render
                            categories)
       :onChange          (fn [e, i, v]
                            (rf/dispatch [:set-task-form-category-id (uuid v)]))
       }
      (->> categories
           (map category-menu-item))]

     [ui/checkbox {:label      "complete"
                   :labelStyle {:color (:primary uic/app-theme)}
                   :style      {:marginTop "20"}
                   :checked    complete
                   :onCheck    (fn [e v]
                                 (rf/dispatch [:set-task-form-complete v]))}]

     [ui/divider {:style {:margin-top    "1em"
                          :margin-bottom "1em"}}]

     (entity-form-buttons [:save-task-form] [:delete-task-form-entity])

     ]
    )
  )

(defn period-form [id]
  (let [desc        @(rf/subscribe [:period-form-description])
        error       @(rf/subscribe [:period-form-error])
        description (if (some? desc) desc "")
        start-d     @(rf/subscribe [:period-form-start])
        stop-d      @(rf/subscribe [:period-form-stop])
        task-id     @(rf/subscribe [:period-form-task-id])
        tasks       @(rf/subscribe [:tasks])
        planned     @(rf/subscribe [:period-form-planned])]

    [:div
     [ui/checkbox {:label      "Planned"
                   :labelStyle {:color (:primary uic/app-theme)}
                   :style      {:marginTop "20"}
                   :checked    planned
                   :onCheck    (fn [e v]
                                 (rf/dispatch [:set-period-form-planned v]))}]
     [ui/subheader "Start"]

     (if (= :time-mismatch error)
       [ui/subheader {:style {:color "red"}}
        "Start must come before Stop"])

     [ui/date-picker {:hintText "Start Date"
                      :value    start-d
                      :onChange
                                (fn [_ new-d]
                                  (rf/dispatch [:set-period-form-date [new-d :start]]))}]

     [ui/time-picker {:hintText "Start Time"
                      :value    start-d
                      :onChange
                                (fn [_ new-s]
                                  (rf/dispatch [:set-period-form-time [new-s :start]]))}]

     [ui/subheader "Stop"]

     (if (= :time-mismatch error)
       [ui/subheader {:style {:color "red"}}
        "Start must come before Stop"])

     [ui/date-picker {:hintText "Stop Date"
                      :value    (if (and (some? start-d)
                                         (nil? stop-d))
                                  start-d
                                  stop-d)
                      :minDate  (when (some? start-d)
                                 start-d)
                      :onChange (fn [_ new-d]
                                  (rf/dispatch [:set-period-form-date [new-d :stop]]))}]

     [ui/time-picker {:hintText "Stop Time"
                      :value    (if-not (some? stop-d)
                                  start-d
                                  stop-d)
                      :onChange
                                (fn [_ new-s]
                                  (rf/dispatch [:set-period-form-time [new-s :stop]]))}]

     [ui/text-field {:floating-label-text "Description"
                     :fullWidth           true
                     :multiLine           true
                     :rows                4
                     :default-value       description
                     :onChange
                                          (fn [e v]
                                            (rf/dispatch-sync [:set-period-form-description v])
                                            )}]

     [ui/select-field
      ;; select fields get a little strange with the parent entity id's
      ;; this goes for tasks and periods
      ;; the value stored on the mui element is a string conversion of the uuid
      ;; the value stored in the app-db form is of uuid type
      ;; function for the renderer, in this select field element, takes in a string id
      ;; but converts it to uuid type before comparing to the collection
      {:value             (str task-id)
       :floatingLabelText "Task"
       :autoWidth         true
       :fullWidth         true
       :errorText         (if (= :no-task error) "Must Select Task")
       :selectionRenderer (partial
                            task-selection-render
                            tasks)
       :onChange          (fn [e, i, v]
                            (rf/dispatch [:set-period-form-task-id (uuid v)]))
       }
      (->> tasks
           (map task-menu-item))]

     [ui/divider {:style {:margin-top    "1em"
                          :margin-bottom "1em"}}]

     (entity-form-buttons [:save-period-form] [:delete-period-form-entity])
     ]
    )
  )

(defn entity-form
  [page-value entity-id]
  (case page-value
        :category (category-form entity-id)
        :task (task-form entity-id)
        :period (period-form entity-id)
        [:div (str page-value " page value doesn't exist")]))
