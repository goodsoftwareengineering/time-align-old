(ns time-align.ui.action-buttons
  (:require [cljs-react-material-ui.reagent :as ui]
            [re-frame.core :as rf]
            [reanimated.core :as anim]
            [time-align.ui.common :as uic]
            [time-align.client-utilities :as cutils]
            [reagent.core :as r]
            [cljs-react-material-ui.icons :as ic]
            [time-align.history :as hist]
            [time-align.js-interop :as jsi]))

(defonce margin-action-expanded (r/atom -20))

(defonce mae-spring (anim/interpolate-to margin-action-expanded))


(def expanded-buttons-style {:style {:display        "flex"
                                     :flex-direction "column"
                                     :align-items    "center"
                                     }})

(defn svg-mui-three-dots []
  [ui/svg-icon
   {:viewBox "0 0 24 24"}
   [:g
    [:circle {:cx "6" :cy "12" :r "2"}]
    [:circle {:cx "12" :cy "12" :r "2"}]
    [:circle {:cx "18" :cy "12" :r "2"}]
    ]]
  )


(def basic-button {:style {}})
(def basic-mini-button {:mini             true
                        :background-color "grey"
                        :style            {:marginBottom "20px"}})

(defn play-button []
  [ui/floating-action-button
   (merge basic-button
          {:onTouchTap
           (fn [e]
             (rf/dispatch
              [:play-period]))})
   [ic/av-play-arrow uic/basic-ic]])

(defn pause-button []
  [ui/floating-action-button
   (merge basic-button
          {:onTouchTap
           (fn [e]
             (rf/dispatch
              [:pause-period-play]))})
   [ic/av-pause uic/basic-ic]])

(defn back-button []
  [ui/floating-action-button (merge
                               basic-button
                               {:secondary true
                                :onTouchTap
                                           (fn [e]
                                             (reset! margin-action-expanded -20)
                                             (rf/dispatch
                                               [:action-buttons-back]))})
   [ic/navigation-close uic/basic-ic]])

(defonce action-buttons-collapsed-click (r/atom false))
(defonce forcer (r/atom 0))

(defn action-buttons-collapsed []
  (let [element (fn [percent]
                  [ui/floating-action-button
                   (merge basic-button
                          {:backgroundColor (cutils/color-gradient
                                              (:primary uic/app-theme)
                                              (:secondary uic/app-theme)
                                              percent)})
                   (svg-mui-three-dots)])]

    (if @action-buttons-collapsed-click
      [anim/timeline
       (element 0)
       ;; 50
       ;; (element 0.33)
       ;; 50
       ;; (element 0.66)
       ;; 50
       ;; (element 0.99)
       ;; 50
       (fn []
         ;; (reset! action-buttons-collapsed-click false)
         (rf/dispatch [:action-buttons-expand])
         )]

      [ui/floating-action-button
       (merge
         basic-button
         {:onTouchTap
          (fn [e]
            (jsi/stop-propagation e)
            (jsi/prevent-default e)
            (reset! action-buttons-collapsed-click true))
          })
       (svg-mui-three-dots)]
      )
    )
  )



(defn action-buttons-no-selection []

    (if @action-buttons-collapsed-click
      (do (reset! action-buttons-collapsed-click false)
          (reset! margin-action-expanded 20)))

    [:div expanded-buttons-style

     [ui/floating-action-button
      (merge basic-mini-button
             {:style   (merge (:style basic-mini-button)
                              {:marginBottom "20"})
              :onClick (fn [e] (hist/nav! "/add"))})

      [ic/content-add uic/basic-ic]]

     (back-button)
     ]
  )

(defn action-buttons-period-selection []
  [:div (if (some? period-in-play) (pause-button) (play-button))])

(defn action-buttons-period-in-play []
  [:div (pause-button)])

(defn action-buttons-queue-selection [selected period-in-play]
  (if @action-buttons-collapsed-click
    (do (reset! action-buttons-collapsed-click false)
        (reset! margin-action-expanded 20)))

  [:div expanded-buttons-style

   (if (some? period-in-play)
     [ui/floating-action-button
      (merge basic-mini-button
             {:style (merge (:style basic-mini-button)
                            {:marginBottom "20"})
              :onTouchTap (fn [e]
                            (rf/dispatch
                             [:pause-period-play]))
              })
      [ic/av-pause uic/basic-ic]]

     [ui/floating-action-button
      (merge basic-mini-button
             {:style (merge (:style basic-mini-button)
                            {:marginBottom "20"})
              :onTouchTap (fn [e]
                            (rf/dispatch
                             [:play-period
                              (:id-or-nil (:current-selection selected))
                              ]))
              })
      [ic/av-play-arrow uic/basic-ic]]
     )

   [ui/floating-action-button
    (merge basic-mini-button
           {:style      (merge (:style basic-mini-button)
                               {:marginBottom "20"})
            :onTouchTap (fn [e]
                            (hist/nav! (str "/edit/period/" (get-in
                                                             selected
                                                             [:current-selection
                                                              :id-or-nil]))))
            })
    [ic/editor-mode-edit uic/basic-ic]]

   (back-button)
   ]
  )

(defn action-buttons [state selected period-in-play]
  (let [forceable @forcer ;; TODO idk what this is for
        selection (get-in selected [:current-selection :type-or-nil])]
    (case selection
      :period (action-buttons-period-selection)
      :queue
      (action-buttons-queue-selection selected period-in-play)
      ;; else
      (if (some? period-in-play)
        (action-buttons-period-in-play selected period-in-play)
        [:div.buttons-empty]))))
