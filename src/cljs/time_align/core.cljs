(ns time-align.core
  (:require [reagent.core :as r]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [re-frame.core :as rf]
            [reanimated.core :as anim]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [time-align.ajax :refer [load-interceptors!]]
            [time-align.handlers]
            [time-align.subscriptions]
            [clojure.string :as string]
            [time-align.utilities :as utils]
            [cljs.pprint :refer [pprint]])
  (:import goog.History))

(def app-theme {:primary   (color :blue-grey-600)
                :secondary (color :red-500)})

(def svg-consts {:viewBox      "0 0 100 100"
                 ;; :width "90" :height "90" :x "5" :y "5"
                 :cx           "50" :cy "50" :r "40"
                 :inner-r      "30"
                 :ticker-r     "5"
                 :center-r     "5"  ;; TODO might not be used
                 :period-width "10"})

(def shadow-filter
  [:defs
   [:filter {:id    "shadow-2dp"
             :x     "-50%" :y      "-100%"
             :width "200%" :height "300%"}
    [:feOffset {:in "SourceAlpha" :result "offA" :dy "2"}]
    [:feOffset {:in "SourceAlpha" :result "offB" :dy "1"}]
    [:feOffset {:in "SourceAlpha" :result "offC" :dy "3"}]
    [:feMorphology {:in       "offC"  :result "spreadC"
                    :operator "erode" :radius "2"}]
    [:feGaussianBlur {:in           "offA" :result "blurA"
                      :stdDeviation "1"}]
    [:feGaussianBlur {:in           "offB" :result "blurB"
                      :stdDeviation "2.5"}]
    [:feGaussianBlur {:in           "spreadC" :result "blurC"
                      :stdDeviation "0.5"}]
    [:feFlood {:flood-opacity "0.14" :result "opA"}]
    [:feFlood {:flood-opacity "0.12" :result "opB"}]
    [:feFlood {:flood-opacity "0.20" :result "opC"}]
    [:feComposite {:in     "opA" :in2      "blurA"
                   :result "shA" :operator "in"}]
    [:feComposite {:in     "opB" :in2      "blurB"
                   :result "shB" :operator "in"}]
    [:feComposite {:in     "opC" :in2      "blurC"
                   :result "shC" :operator "in"}]
    [:feMerge
     [:feMergeNode {:in "shA"}]
     [:feMergeNode {:in "shB"}]
     [:feMergeNode {:in "shC"}]
     [:feMergeNode {:in "SourceGraphic"}]]]])

(defonce margin-action-expanded (r/atom -20))
(defonce mae-spring (anim/interpolate-to margin-action-expanded))

(defn describe-arc [cx cy r start stop]
  (let [
        p-start (utils/polar-to-cartesian cx cy r start)
        p-stop  (utils/polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- stop start) 180) "0" "1")]

    (string/join " " ["M" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 1 (:x p-stop) (:y p-stop)])))

(defn period [selected curr-time is-moving-period type period]
  (let [id          (:id period)
        start-date  (:start period)
        start-ms    (utils/get-ms start-date)
        start-angle (utils/ms-to-angle start-ms)

        stop-date  (:stop period)
        stop-ms    (utils/get-ms stop-date)
        stop-angle (utils/ms-to-angle stop-ms)

        curr-time-ms (.valueOf curr-time)
        start-abs-ms (.valueOf start-date)
        stop-abs-ms  (.valueOf stop-date)

        is-period-selected   (= :period
                                (get-in
                                 selected
                                 [:current-selection :type-or-nil]))
        selected-period      (if is-period-selected
                               (get-in
                                selected
                                [:current-selection :id-or-nil])
                               nil)
        this-period-selected (= selected-period id)

        opacity (cond
                  this-period-selected         "0.9"
                  (> curr-time-ms stop-abs-ms) "0.2"
                  :else                        "0.7")



        color        (cond
                       (or (nil? selected-period)
                           (= selected-period id))
                       (:color period)
                       (and (some? selected-period)
                            (not= selected-period id))
                       "#aaaaaa"
                       :else "#000000")
        period-width (js/parseInt (:period-width svg-consts))
        cx           (js/parseInt (:cx svg-consts))
        cy           (js/parseInt (:cy svg-consts))
        ;; radii need to be offset to account for path using
        ;; A (arc) command having radius as the center of path
        ;; instead of edge (like circle)
        r            (-> (case type
                           :actual  (:r svg-consts)
                           :planned (:inner-r svg-consts)
                           (* 0.5 (:inner-r svg-consts)))
                         (js/parseInt )
                         (- (/ period-width 2)))

        arc                      (describe-arc cx cy r start-angle stop-angle)
        touch-click-handler      (if (not is-period-selected)
                                   (fn [e]
                                     (.stopPropagation e)
                                     (.preventDefault e)
                                     (rf/dispatch
                                      [:set-selected-period id])))
        movement-trigger-handler (if (and is-period-selected
                                          (= selected-period id))
                                   (fn [e]
                                     (.stopPropagation e)
                                     (rf/dispatch
                                      [:set-moving-period true]))
                                   )]
    [:g {:key (str id)}
     [:path
      {:d            arc
       :stroke       color
       :opacity      opacity
       :stroke-width period-width
       :fill         "transparent"
       :onClick      touch-click-handler
       :onTouchStart movement-trigger-handler
       :onMouseDown  movement-trigger-handler
       }]
     ]
    ))

(defn periods [periods selected is-moving-period curr-time]
  (let [actual  (:actual-periods periods)
        planned (:planned-periods periods)]
    [:g
     [:g
      (if (some? actual)
        (->> actual
             (map (fn [actual-period] (period selected
                                              curr-time
                                              is-moving-period
                                              :actual
                                              actual-period)))))]
     [:g
      (if (some? planned)
        (->> planned
             (map (fn [planned-period] (period selected
                                               curr-time
                                               is-moving-period
                                               :planned
                                               planned-period))))
        )]]))

(defn handle-period-move [id type evt]
  (let [cx                (js/parseInt (:cx svg-consts))
        cy                (js/parseInt (:cy svg-consts))
        pos               (utils/client-to-view-box id evt type)
        pos-t             (utils/point-to-centered-circle
                           (merge pos {:cx cx :cy cy}))
        angle             (utils/point-to-angle pos-t)
        mid-point-time-ms (utils/angle-to-ms angle)]

    (println (str "moved " type))
    (rf/dispatch [:move-selected-period mid-point-time-ms])))

(defn x-svg [{:keys [cx cy r fill stroke shadow click] }]
  (let [pi        (.-PI js/Math)
        cx-int    (js/parseInt cx)
        cy-int    (js/parseInt cy)
        r-int     (js/parseInt r)
        r-int-adj (* 0.70 r-int)

        x1 (+ cx-int (* r-int-adj (.cos js/Math (* pi (/ 3 4)))))
        y1 (+ cy-int (* r-int-adj (.sin js/Math (* pi (/ 3 4)))))
        x2 (+ cx-int (* r-int-adj (.cos js/Math (* pi (/ 7 4)))))
        y2 (+ cy-int (* r-int-adj (.sin js/Math (* pi (/ 7 4)))))

        x3 (+ cx-int (* r-int-adj (.cos js/Math (* pi (/ 1 4)))))
        y3 (+ cy-int (* r-int-adj (.sin js/Math (* pi (/ 1 4)))))
        x4 (+ cx-int (* r-int-adj (.cos js/Math (* pi (/ 5 4)))))
        y4 (+ cy-int (* r-int-adj (.sin js/Math (* pi (/ 5 4)))))

        generic {:fill           "transparent"
                 :stroke         stroke
                 :stroke-width   "1"
                 :stroke-linecap "round"}]

    [:g {:onClick click}
     [:circle (merge {:fill fill :cx cx :cy cy :r r}
                   (if shadow
                     {:filter "url(#shadow-2dp)"}
                     {}))]

   [:path (merge generic
                 {:d (str "M " x1 " " y1 " " "L " x2 " " y2 " ")})]
   [:path (merge generic
                 {:d (str "M " x3 " " y3 " " "L " x4 " " y4 " ")})]
   ]))

(defn +-svg [{:keys [cx cy r fill stroke shadow click] }]
  (let [pi        (.-PI js/Math)
        cx-int    (js/parseInt cx)
        cy-int    (js/parseInt cy)
        r-int     (js/parseInt r)
        r-int-adj (* 0.70 r-int)

        x1 (+ cx-int (* r-int-adj (.cos js/Math (* pi (/ 1 2)))))
        y1 (+ cy-int (* r-int-adj (.sin js/Math (* pi (/ 1 2)))))
        x2 (+ cx-int (* r-int-adj (.cos js/Math (* pi (/ 3 2)))))
        y2 (+ cy-int (* r-int-adj (.sin js/Math (* pi (/ 3 2)))))

        x3 (+ cx-int (* r-int-adj (.cos js/Math pi)))
        y3 (+ cy-int (* r-int-adj (.sin js/Math pi)))
        x4 (+ cx-int (* r-int-adj (.cos js/Math (* pi 2))))
        y4 (+ cy-int (* r-int-adj (.sin js/Math (* pi 2))))

        generic {:fill           "transparent"
                 :stroke         stroke
                 :stroke-width   "1"
                 :stroke-linecap "round"}]

    [:g {:onClick click}
     [:circle (merge {:fill fill :cx cx :cy cy :r r}
                   (if shadow
                     {:filter "url(#shadow-2dp)"}
                     {}))]
   [:path (merge generic
                 {:d (str "M " x1 " " y1 " " "L " x2 " " y2 " ")})]
   [:path (merge generic
                 {:d (str "M " x3 " " y3 " " "L " x4 " " y4 " ")})]
   ]))

(defn --svg [{:keys [cx cy r fill stroke shadow click] }]
  (let [pi        (.-PI js/Math)
        cx-int    (js/parseInt cx)
        cy-int    (js/parseInt cy)
        r-int     (js/parseInt r)
        r-int-adj (* 0.70 r-int)

        x3 (+ cx-int (* r-int-adj (.cos js/Math pi)))
        y3 (+ cy-int (* r-int-adj (.sin js/Math pi)))
        x4 (+ cx-int (* r-int-adj (.cos js/Math (* pi 2))))
        y4 (+ cy-int (* r-int-adj (.sin js/Math (* pi 2))))

        generic {:fill           "transparent"
                 :stroke         stroke
                 :stroke-width   "1"
                 :stroke-linecap "round"}]

    [:g {:onClick click}
     [:circle (merge {:fill fill :cx cx :cy cy :r r}
                   (if shadow
                     {:filter "url(#shadow-2dp)"}
                     {}))]
   [:path (merge generic
                 {:d (str "M " x3 " " y3 " " "L " x4 " " y4 " ")})]
   ]))

(defn zoom-in-buttons []
  (let [basics {:fill   "#c2c2c2"
                :stroke "#b2b2b2"
                :shadow false
                :r      5
                }]
    [:g
     (+-svg (merge basics
                   {:cx    10 :cy 10
                    :click (fn [e]
                             (rf/dispatch [:set-zoom :q1]))}))
     (+-svg (merge basics
                   {:cx    90 :cy 10
                    :click (fn [e]
                             (rf/dispatch [:set-zoom :q2]))}))
     (+-svg (merge basics
                   {:cx    10 :cy 90
                    :click (fn [e]
                             (rf/dispatch [:set-zoom :q3]))}))
     (+-svg (merge basics
                   {:cx    90 :cy 90
                    :click (fn [e]
                             (rf/dispatch [:set-zoom :q4]))}))
     ]))

(defn zoom-out-buttons []
  (let [basics {:fill   "#d2d2d2"
                :stroke "#a2a2a2"
                :shadow true
                :r      5
                }]
    [:g
     (--svg (merge basics
                   {:cx    10 :cy 10
                    :click (fn [e]
                             (rf/dispatch [:set-zoom nil]))}))
     (--svg (merge basics
                   {:cx    90 :cy 10
                    :click (fn [e]
                             (rf/dispatch [:set-zoom nil]))}))
     (--svg (merge basics
                   {:cx    10 :cy 90
                    :click (fn [e]
                             (rf/dispatch [:set-zoom nil]))}))
     (--svg (merge basics
                   {:cx    90 :cy 90
                    :click (fn [e]
                             (rf/dispatch [:set-zoom nil]))}))
     ]))

(defonce clock-state (r/atom {:time (new js/Date)}))

(defn clock-tick []
  (swap! clock-state assoc :time (new js/Date))
  ;; TODO think about putting clock state in db
  ;; have handler that ticks the clock + resets any "playing" period
  )

(defn day [tasks selected day]
  (let [date-str                 (subs (.toISOString day) 0 10)
        curr-time                (:time @clock-state)
        display-ticker           (= (.valueOf (utils/zero-in-day day))
                                    (.valueOf (utils/zero-in-day curr-time)))
        ticker-ms                (utils/get-ms curr-time)
        ticker-angle             (utils/ms-to-angle ticker-ms)
        ticker-pos               (utils/polar-to-cartesian
                                  (:cx svg-consts)
                                  (:cy svg-consts)
                                  (:r svg-consts)
                                  ticker-angle)
        zoom                     @(rf/subscribe [:zoom])
        filtered-periods         (utils/filter-periods-for-day day tasks)
        selected-period          (if (= :period
                                        (get-in
                                         selected
                                         [:current-selection :type-or-nil]))
                                   (get-in selected
                                           [:current-selection :id-or-nil])
                                   nil)
        is-moving-period         @(rf/subscribe [:is-moving-period])
        stop-touch-click-handler (if is-moving-period
                                   (fn [e]
                                     (.preventDefault e)
                                     (rf/dispatch
                                      [:set-moving-period false])))
        deselect                 (if (not is-moving-period)
                                   (fn [e]
                                     (.preventDefault e)
                                     (rf/dispatch
                                      [:set-selected-period nil])))]

    (js/setTimeout clock-tick 1000)

    [:svg (merge {:key         date-str
                  :id          date-str
                  :style       {:display      "inline-box"
                                :touch-action "pinch-zoom"
                                ;; this stops scrolling
                                ;; for moving period
                                }
                  :width       "100%"
                  :height      "100%"
                  :onTouchEnd  stop-touch-click-handler
                  :onMouseUp   stop-touch-click-handler
                  :onTouchMove (if is-moving-period
                                 (partial handle-period-move
                                          date-str :touch))
                  :onMouseMove (if is-moving-period
                                 (partial handle-period-move
                                          date-str :mouse))
                  :onClick     deselect
                  }
                 (case zoom
                   :q1 {:viewBox "0 0 60 60"}
                   :q2 {:viewBox "40 0 60 60"}
                   :q3 {:viewBox "0 40 60 60"}
                   :q4 {:viewBox "40 40 60 60"}
                   (select-keys svg-consts [:viewBox])
                   ))
     shadow-filter
     [:circle (merge {:fill "#e8e8e8" :filter "url(#shadow-2dp)"}
                     (select-keys svg-consts [:cx :cy :r]))]
     [:circle (merge {:fill "#f1f1f1" :r (:inner-r svg-consts)}
                     (select-keys svg-consts [:cx :cy]))]

     (if display-ticker
       [:g
        [:line {:fill         "transparent"
                :stroke-width "1"
                :stroke       "white"
                :filter       "url(#shadow-2dp)"
                :x1           (:cx svg-consts)
                :y1           (:cy svg-consts)
                :x2           (:x ticker-pos)
                :y2           (:y ticker-pos)}]
        [:circle (merge {:fill   "white"
                         :filter "url(#shadow-2dp)"
                         :r      (:ticker-r svg-consts)}
                        (select-keys svg-consts [:cx :cy]))]
        ]
       )

     (periods filtered-periods selected is-moving-period curr-time)
     ]))

(defn days [days tasks selected-period]
  (->> days
       (map (partial day tasks selected-period))))

(defn task-list [tasks]
  [:div.tasks-list {:style {:display "flex"}}
   [ui/paper
    [:div.task-list {:style {:overflow-y "scroll"}}
     [ui/list
      (->> tasks
           (map
            (fn [t]
              [ui/list-item
               {:key         (:id t)
                :primaryText (:name t)
                :onTouchTap  #(rf/dispatch
                               [:set-selected-task (:id t)])
                }
               ])))]]]])

(defn svg-mui-circle [color]
  [ui/svg-icon
   {:viewBox "0 0 24 24" :style {:margin-left "0.5em"}}
   [:circle {:cx "12" :cy "12" :r "11" :fill color}]])

(defn concatonated-text
  "Takes a text message, a cut off character limit, and a fall back message. Returns text concatonated with ellipsis, full text if it is less than 10 characters or an (r/element) styled grey if text is empty."
  [text character-limit if-empty-message]
  (if (and (some? text)
           (not (empty? text)))
    (if (< character-limit (count text))
      (str (string/join "" (take character-limit text)) " ...")
      text)
    (r/as-element
     [:span {:style {:text-decoration "italic"
                     :color           "grey"}}
      if-empty-message]))
  )

(defn queue [tasks selected]
  (let [periods-no-stamps (utils/filter-periods-no-stamps tasks)
        sel               (:current-selection selected)
        ]
    [ui/list {:style {:width "100%"}}
        (->> periods-no-stamps
             (map (fn [period]
                    [ui/list-item
                     {:style       (merge {:width "100%"}
                                          (if (and (= :queue (:type-or-nil sel))
                                                   (= (:id period) (:id-or-nil sel)))
                                            {:backgroundColor (color :grey-300)}
                                            {}))
                      :key         (:id period)
                      :leftIcon    (r/as-element
                                    [ui/svg-icon [ic/action-list {:color (:color period)}]])
                      :primaryText (concatonated-text (:description period) 10 "No period description ...")
                      :onTouchTap  (fn [e]
                                     (rf/dispatch
                                      [:set-selected-queue (:id period)])
                                     )}])))]))

(def basic-button {:style {}})
(def basic-mini-button {:mini             true
                        :background-color "grey"
                        :style            {:marginBottom "20px"}})
(def basic-ic {:style {:marginTop "7.5px"}
               :color "white"})
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

(defn svg-mui-stretch []
  [ui/svg-icon
   {:viewBox "0 0 24 24"}
   [:g
    [:polyline {:points "7,2 2,12 7,22"}]
    [:polyline {:points "11,2 13,2 13,22 11,22"}]
    [:polyline {:points "17,2 22,12 17,22"}]
    ]])

(defn svg-mui-shrink []
  [ui/svg-icon
   {:viewBox "0 0 24 24"}
   [:g
    [:polyline {:points "2,2 7,12 2,22"}]
    [:polyline {:points "11,2 13,2 13,22 11,22"}]
    [:polyline {:points "22,2 17,12 22,22"}]
    ]]
  )

(defn back-button []
  [ui/floating-action-button (merge
                              basic-button
                              {:secondary true
                               :onTouchTap
                               (fn [e]
                                 (reset! margin-action-expanded -20)
                                 (rf/dispatch
                                  [:action-buttons-back]))})
   [ic/navigation-close basic-ic]])

(defonce action-buttons-collapsed-click (r/atom false))
(defonce forcer (r/atom 0))

(defn action-buttons-collapsed []
  (let [element (fn [percent]
                  [ui/floating-action-button
                   (merge basic-button
                          {:backgroundColor (utils/color-gradient
                                             (:primary app-theme)
                                             (:secondary app-theme)
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
         (println "dispatching")
         ;; (reset! action-buttons-collapsed-click false)
         (rf/dispatch [:action-buttons-expand])
         )]

      [ui/floating-action-button
       (merge
        basic-button
        {:onTouchTap
         (fn [e]
           (.stopPropagation e)
           (.preventDefault e)
           (reset! action-buttons-collapsed-click true))
         })
       (svg-mui-three-dots)]
      )
    )
  )

(defn svg-mui-entity [{:keys [type color style]}]
  [ui/svg-icon
   (merge {:style style} {:viewBox "0 0 24 24"})
   [:g
    [:rect {:x            "2" :y "2" :width "4" :height "4"
            :fill         (if (= type :category) color "transparent")
            :stroke       color
            :stroke-width "2"}]
    [:path {:d            "M 4 6 V 12 H 10"
            :stroke       color
            :fill         "transparent"
            :stroke-width "2"}]
    [:rect {:x            "10" :y "10" :width "4" :height "4"
            :fill         (if (= type :task) color "transparent")
            :stroke       color
            :stroke-width "2"}]
    [:path {:d            "M 12 14 V 20 H 18"
            :stroke       color
            :fill         "transparent"
            :stroke-width "2"}]
    [:rect {:x            "18" :y "18" :width "4" :height "4"
            :fill         (if (= type :period) color "transparent")
            :stroke       color
            :stroke-width "2"}]
    ]])

(defn action-buttons-no-selection []
  (let [zoom @(rf/subscribe [:zoom])
        ;; spring @mae-spring
        ]

    (if @action-buttons-collapsed-click
      (do (reset! action-buttons-collapsed-click false)
          (reset! margin-action-expanded 20) ))

    [:div expanded-buttons-style

     [ui/floating-action-button
      (merge basic-mini-button
             {:style   (merge (:style basic-mini-button)
                              {:marginBottom "20"})
              :onClick (fn [e]
                         (rf/dispatch [:set-active-page
                                       {:page-id :entity-forms
                                        :type    :category
                                        :id      nil}]))})

      [ic/content-add basic-ic]]

     (if (some? zoom)
       [ui/floating-action-button
        (merge basic-mini-button
               {:style (merge (:style basic-mini-button)
                              {:marginBottom "20"})})
        [ic/action-zoom-out basic-ic]]

       [ui/floating-action-button
        (merge basic-mini-button
               {:style (merge (:style basic-mini-button)
                              {:marginBottom "20"})})
        [ic/action-zoom-in basic-ic]])

     (back-button)
     ]
    )
  )

(defn action-buttons-period-selection [selected]
  (if @action-buttons-collapsed-click
    (do (reset! action-buttons-collapsed-click false)
        (reset! margin-action-expanded 20) ))

  [:div expanded-buttons-style

   ;; TODO this switches with pause depending on play state
   ;; shouldn't be too bad, the ticker makes the
   [ui/floating-action-button
    (merge basic-mini-button
           {:style (merge (:style basic-mini-button)
                          {:marginBottom "20"})})
    [ic/av-play-arrow basic-ic]]

   [ui/floating-action-button
    (merge basic-mini-button
           {:style (merge (:style basic-mini-button)
                          {:marginBottom "20"})})
    (svg-mui-stretch)]

   [ui/floating-action-button
    (merge basic-mini-button
           {:style (merge (:style basic-mini-button)
                          {:marginBottom "20"})})
    (svg-mui-shrink)]

   [ui/floating-action-button
    (merge basic-mini-button
           {:style      (merge (:style basic-mini-button)
                               {:marginBottom "20"})
            :onTouchTap (fn [e]
                          (rf/dispatch
                           [:set-active-page
                            {:page-id :entity-forms
                             :type    :period
                             :id      (get-in
                                       selected
                                       [:current-selection
                                        :id-or-nil])}]))})

    [ic/editor-mode-edit basic-ic]]

   (back-button)
   ]
  )

(defn action-buttons-queue-selection [selected]
  (if @action-buttons-collapsed-click
    (do (reset! action-buttons-collapsed-click false)
        (reset! margin-action-expanded 20) ))

  [:div expanded-buttons-style

   ;; TODO this switches with pause depending on play state
   ;; shouldn't be too bad, the ticker makes the
   [ui/floating-action-button
    (merge basic-mini-button
           {:style (merge (:style basic-mini-button)
                          {:marginBottom "20"})})
    [ic/av-play-arrow basic-ic]]

   [ui/floating-action-button
    (merge basic-mini-button
           {:style      (merge (:style basic-mini-button)
                               {:marginBottom "20"})
            :onTouchTap (fn [e]
                          (rf/dispatch
                           [:set-active-page
                            {:page-id :entity-forms
                             :type    :period
                             :id      (get-in
                                       selected
                                       [:current-selection
                                        :id-or-nil])}]))})
    [ic/editor-mode-edit basic-ic]]

   (back-button)
   ]
  )

(defn action-buttons [state selected]
  (let [forceable @forcer]
    (case state
      :collapsed
      (action-buttons-collapsed)
      :no-selection
      (action-buttons-no-selection)
      :period
      (action-buttons-period-selection selected)
      :queue
      (action-buttons-queue-selection selected)
      [:div "no buttons!"]
      )
    )
  )

(defn svg-mui-time-align [{:keys [color style]}]
  [ui/svg-icon
   (merge {:style style} {:viewBox "0 0 24 24"})
   [:g
    [:circle {:cx           "12" :cy "12" :r "10"
              :stroke-width "2"
              :stroke       color
              :fill         "transparent"}]
    [:path {:d            "M 12 12 L 9 18"
            :stroke-width "2"
            :stroke       color
            :fill         "transparent"}]
    ]
   ]
  )

(defn svg-mui-task-symbol [{:keys [color style]}]
  [ui/svg-icon
   (merge {:style style} {:viewBox "0 0 24 24"})
   [:g

    [:polyline {:points "12,1 1,12 12,23 23,12 12,1"
                :fill   color}]
    ]
   ]
  )

(defn app-bar []
  (let [
        main-drawer-state @(rf/subscribe [:main-drawer-state])
        ]
    [:div.app-bar-container
     {:style {:display    "flex"
              :flex       "1 0 100%"
              ;; :border "green solid 0.1em"
              :box-sizing "border-box"}}
     [ui/app-bar {:title                    "Time Align"
                  :onLeftIconButtonTouchTap (fn [e] (rf/dispatch [:toggle-main-drawer]))}]
     [ui/drawer {:docked             false
                 :open               main-drawer-state
                 :disableSwipeToOpen true
                 :onRequestChange    (fn [new-state] (rf/dispatch [:set-main-drawer new-state]))}
      [ui/menu-item {:onTouchTap    #(do
                                       (rf/dispatch [:set-main-drawer false])
                                       (rf/dispatch [:set-active-page {:page-id :list}]))
                     :innerDivStyle {:display "flex" :align-items "center"}}
       (svg-mui-entity {:type :all :color "black" :style {:marginRight "0.5em"}})
       [:span "List"]]
      [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                     :innerDivStyle {:display "flex" :align-items "center"}}
       [ic/social-person {:style {:marginRight "0.5em"}}]
       [:span "Account"]]
      [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                     :innerDivStyle {:display "flex" :align-items "center"}}
       [ic/action-settings {:style {:marginRight "0.5em"}}]
       [:span "Settings"]]
      [ui/menu-item {:onTouchTap    #(do
                                       (rf/dispatch [:set-main-drawer false])
                                       (rf/dispatch [:set-active-page :home]))
                     :innerDivStyle {:display "flex" :align-items "center"}}
       (svg-mui-time-align {:color "black"
                            :style {:marginRight "0.5em"}})
       [:span "Home"]]
      ]]
    )
  )

(defn home-page []
  (let [
        tasks               @(rf/subscribe [:tasks])
        selected            @(rf/subscribe [:selected])
        action-button-state @(rf/subscribe [:action-buttons])
        ]

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
               :box-sizing "border-box"}}
      (day tasks selected (new js/Date))]

     [:div.queue-container
      {:style {:display    "flex"
               :flex       "1 0 100%"
               ;; :border "blue solid 0.1em"
               :box-sizing "border-box"}}
      [ui/paper {:style {:width "100%"}}
       (queue tasks selected)
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
      (action-buttons action-button-state selected)]]))

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
                                                     (utils/color-hex->255 c)]))}]))
        )
   ])

(defn color-slider [color]
  [:div.slider
   [ui/slider {:value    (:red color)
               :min      0
               :max      255
               :onChange (fn [e v]
                           (rf/dispatch [:set-category-form-color
                                         {:red (.ceil js/Math v)}]))}]
   [ui/slider {:value    (:green color)
               :min      0
               :max      255
               :onChange (fn [e v]
                           (rf/dispatch [:set-category-form-color
                                         {:green (.ceil js/Math v)}]))}]
   [ui/slider {:value    (:blue color)
               :min      0
               :max      255
               :onChange (fn [e v]
                           (rf/dispatch [:set-category-form-color
                                         {:blue (.ceil js/Math v)}]))}]
   ]
   )

(defn entity-form-chooser [type]
  [:div.entity-selection
   [ui/flat-button {:label    "Category"
                    :disabled (= type :category)
                    :primary  (not= type :category)
                    ;; TODO get sizing right
                    ;; TODO disable coloring on icon
                    ;; :icon (r/as-element
                    ;;        (svg-mui-entity
                    ;;         {:type :category
                    ;;          :color (:primary app-theme)
                    ;;          :style {}}))
                    :onClick  (fn [e]
                                (rf/dispatch
                                 [:set-active-page
                                  {:page-id :entity-forms
                                   :type    :category
                                   :id      nil}]))}]
   [ui/flat-button {:label    "Task"
                    :disabled (= type :task)
                    :primary  (not= type :task)
                    ;; :icon (r/as-element
                    ;;        (svg-mui-entity
                    ;;         {:type :task
                    ;;          :color (:primary app-theme)
                    ;;          :style {}}))
                    :onClick  (fn [e]
                                (rf/dispatch
                                 [:set-active-page
                                  {:page-id :entity-forms
                                   :type    :task
                                   :id      nil}]))}]
   [ui/flat-button {:label    "Period"
                    :disabled (= type :period)
                    :primary  (not= type :period)
                    ;; :icon (r/as-element
                    ;;        (svg-mui-entity
                    ;;         {:type :period
                    ;;          :color (:primary app-theme)
                    ;;          :style {}}))
                    :onClick  (fn [e]
                                (rf/dispatch
                                 [:set-active-page
                                  {:page-id :entity-forms
                                   :type    :period
                                   :id      nil}]))}]
   ]
  )

(defn entity-form-buttons [back-page-id save-dispatch-vec delete-dispatch-vec]
  [:div.buttons {:style {:display         "flex"
                         :justify-content "space-between"
                         :margin-top      "1em"
                         }}

   [ui/flat-button {:icon            (r/as-element [ic/action-delete-forever basic-ic])
                    :backgroundColor (:secondary app-theme)
                    :onTouchTap      (fn [e]
                                       (rf/dispatch delete-dispatch-vec)
                                       )}]

   [ui/flat-button {:icon            (r/as-element [ic/navigation-cancel basic-ic])
                    :backgroundColor "grey"
                    :onTouchTap      (fn [e]
                                       (rf/dispatch [:set-active-page
                                                     {:pageId back-page-id
                                                      :type   nil
                                                      :id     nil}])
                                       )}]
   [ui/flat-button {:icon            (r/as-element [ic/content-save basic-ic])
                    :backgroundColor (:primary app-theme)
                    :onTouchTap      (fn [e]
                                       (rf/dispatch save-dispatch-vec)
                                       )}]
   ])

(defn category-form [id]
  (let [color @(rf/subscribe [:category-form-color])
        name  @(rf/subscribe [:category-form-name])]

    [:div.category-form {:style {:padding         "0.5em"
                                 :backgroundColor "white"}}

     (entity-form-chooser :category)

     [ui/text-field {:floating-label-text "Name"
                     :value               name
                     :onChange            (fn [e v]
                                            (rf/dispatch [:set-category-form-name v]))}]

     [ui/divider {:style {:margin-top    "1em"
                          :margin-bottom "1em"}}]

     [:div.colorHeader {:style {:display         "flex"
                                :flexWrap        "nowrap"
                                :align-items     "center"
                                :justify-content "space-around"}}
      [ui/svg-icon {:viewBox "0 0 1000 1000" :style {:margin-left "0.5em"}}
       [:circle {:cx "500" :cy "500" :r "500" :fill (utils/color-255->hex color)}]]
      [ui/subheader "Color"]]

     [ui/tabs {:tabItemContainerStyle {:backgroundColor "white"}
               :inkBarStyle           {:backgroundColor (:primary app-theme)}}
      [ui/tab {:label "picker" :style {:color (:primary app-theme)}}
       (standard-color-picker)]
      [ui/tab {:label "slider" :style {:color (:primary app-theme)}}
       (color-slider color)]]

     [ui/divider {:style {:margin-top    "1em"
                          :margin-bottom "1em"}}]

     (entity-form-buttons :home [:save-category-form] [:delete-category-form-entity])
     ]
    )
  )

(defn category-menu-item [category]
  (let [id (str (:id category))]
    [ui/menu-item
     {:key         id
      :value       id
      :primaryText (:name category)
      :leftIcon    (r/as-element
                    (svg-mui-circle (:color category)) )}]
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
                    (svg-mui-circle (:color task)) )}]
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

    [:div.task-form {:style {:padding         "0.5em"
                             :backgroundColor "white"}}

     (entity-form-chooser :task)

     [ui/text-field {:floating-label-text "Name"
                     :value               name
                     :fullWidth           true
                     :onChange
                     (fn [e v]
                       (rf/dispatch [:set-task-form-name v])
                       )}]

     [ui/text-field {:floating-label-text "Description"
                     :value               description
                     :fullWidth           true
                     :multiLine           true
                     :rows                4
                     :onChange
                     (fn [e v]
                       (rf/dispatch [:set-task-form-description v])
                       )}]

     [ui/select-field
      {:value             category-id
       :floatingLabelText "Category"
       :autoWidth         true
       :fullWidth         true
       :selectionRenderer (partial
                           category-selection-render
                           categories)
       :onChange          (fn [e, i, v]
                            (rf/dispatch [:set-task-form-category-id v]))
       }
      (->> categories
           (map category-menu-item))]

     [ui/checkbox {:label      "complete"
                   :labelStyle {:color (:primary app-theme)}
                   :style      {:marginTop "20"}
                   :checked    complete
                   :onCheck    (fn [e v]
                                 (rf/dispatch [:set-task-form-complete v]))}]

     (entity-form-buttons :home [:submit-task-form] [:delete-task-form-entity])

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

    [:div.task-form {:style {:padding         "0.5em"
                             :backgroundColor "white"}}

     (entity-form-chooser :period)

     [ui/checkbox {:label      "Planned"
                   :labelStyle {:color (:primary app-theme)}
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
                      :value    stop-d
                      :onChange
                      (fn [_ new-d]
                        (rf/dispatch [:set-period-form-date [new-d :stop]]))}]

     [ui/time-picker {:hintText "Stop Time"
                      :value    stop-d
                      :onChange
                      (fn [_ new-s]
                        (rf/dispatch [:set-period-form-time [new-s :stop]]))}]

     [ui/text-field {:floating-label-text "Description"
                     :value               description
                     :fullWidth           true
                     :multiLine           true
                     :rows                4
                     :onChange
                     (fn [e v]
                       (rf/dispatch [:set-period-form-description v])
                       )}]

     [ui/select-field
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

     (entity-form-buttons :home [:save-period-form] [:delete-period-form-entity])
     ]
    )
  )

(defn entity-forms [page]
  (let [page-value (if-let [entity-type (:type-or-nil page)]
                     entity-type
                     :category)
        entity-id  (:id-or-nil page)
        ]
    [:div.entity-form-container
     (app-bar)

     (case page-value
       :category (category-form entity-id)
       :task     (task-form entity-id)
       :period   (period-form entity-id)
       [:div (str page-value " page value doesn't exist")]
       )
     ]
    )
  )

(defn list-period [current-selection period]
  (let [{:keys [id description color]} period
        sel-id            (:id-or-nil current-selection)
        sel-cat           (:type-or-nil current-selection)
        is-selected       (and (= :period sel-cat)
                               (= id sel-id))
        is-child-selected false]

    (r/as-element
     [ui/list-item
      (merge {:key         id
              :primaryText (concatonated-text description 10 "no description provided ...")
              :style       (if is-selected {:backgroundColor "#dddddd"})
              :onClick     (fn [e]
                             (if is-selected
                               (println "I'm selected")
                               (rf/dispatch [:set-selected {:type :period :id id}])))}

             (if (and (some? (:start period))
                      (some? (:stop period)))

               ;; if not queue render the arc
               (let [start       (:start period)
                     start-ms    (utils/get-ms start)
                     start-angle (utils/ms-to-angle start-ms)
                     stop        (:stop period)
                     stop-ms     (utils/get-ms stop)
                     stop-angle  (utils/ms-to-angle stop-ms)

                     angle-difference     (- stop-angle start-angle)
                     minimum-angle        30
                     factor-change        (- minimum-angle angle-difference)
                     ;; adjustments seek to set the angle difference to minimum (no matter what it is)
                     ;; catches when one side goes over edge with max & min
                     start-angle-adjusted (.max js/Math
                                                (- start-angle (/ factor-change 2))
                                                1)
                     stop-angle-adjusted  (.min js/Math
                                                (+ stop-angle (/ factor-change 2))
                                                359)

                     use-adjustment (< angle-difference 20)
                     start-used     (if use-adjustment
                                      start-angle-adjusted
                                      start-angle)
                     stop-used      (if use-adjustment
                                      stop-angle-adjusted
                                      stop-angle)]

                 {:leftIcon (r/as-element
                             [ui/svg-icon
                              {:viewBox "0 0 24 24"}
                              [:g
                               [:circle {:cx           "12" :cy     "12" :r "11"
                                         :stroke-width "2"  :stroke "#cdcdcd"
                                         :fill         "transparent"}]
                               [:path
                                {:d            (describe-arc 12 12 11 start-used stop-used)
                                 :stroke       color
                                 :stroke-width "2"
                                 :fill         "transparent"
                                 }]]])})

               ;; otherwise render a queue indicator
               {:leftIcon (r/as-element
                           [ui/svg-icon [ic/action-list {:color color}]])}))])))

(defn list-task [current-selection task]
  (let [{:keys [id name actual-periods complete planned-periods color]} task

        periods (concat
                 (map #(assoc % :type :actual) actual-periods)
                 (map #(assoc % :type :planned) planned-periods))

        periods-with-color (->> periods (map #(assoc % :color color)))
        periods-sorted     (reverse
                            (sort-by #(if (some? (:start %))
                                        (.valueOf (:start %))
                                        0) periods-with-color))
        sel-id      (:id-or-nil current-selection)
        sel-cat     (:type-or-nil current-selection)
        is-selected (and (= :task sel-cat)
                         (= id sel-id))
        is-child-selected (->> task
                               ((fn [task] ;; pulls periods into one seq
                                  (concat (:planned-periods task) (:actual-periods task))))
                               (some #(= sel-id (:id %))))]
    (r/as-element
     [ui/list-item
      {:key         id
       :primaryText (concatonated-text name 15 "no name entered ...")
       :nestedItems (->> periods-sorted
                         (map (partial list-period current-selection)))
       :leftIcon    (r/as-element
                     [ui/checkbox {:checked   complete
                                   :iconStyle {:fill color}}])
       :open        (or is-selected
                        is-child-selected)
       :style       (if is-selected {:backgroundColor "#dddddd"})
       :onClick     (fn [e]
                      (if is-selected
                        (println "I'm selected")
                        (rf/dispatch [:set-selected {:type :task :id id}])))
       }])))

(defn list-category [current-selection category]
  (let [{:keys [id name color tasks]} category
        sel-id      (:id-or-nil current-selection)
        sel-cat     (:type-or-nil current-selection)
        is-selected (and (= :category sel-cat)
                         (= id sel-id))
        is-child-selected (->> category
                               (:tasks)
                               (some #(= sel-id (:id %))))]

    [ui/list-item {:key         id
                   :primaryText (concatonated-text name 20
                                                   "no name entered ...")
                   :leftIcon    (r/as-element (svg-mui-circle color))
                   :nestedItems (->> tasks
                                     (map #(assoc % :color color))
                                     (map (partial list-task current-selection)))
                   :open        (or is-selected
                                    is-child-selected)
                   :style       (if is-selected {:backgroundColor "#dddddd"})
                   :onClick     (fn [e]
                                  (if is-selected
                                    (println "I'm selected")
                                    (rf/dispatch [:set-selected {:type :category :id id}])))
                   }]
    )
  )

(defn list-page []
  (let [categories @(rf/subscribe [:categories])
        selected @(rf/subscribe [:selected])
        current-selection (:current-selection selected)]

    [:div
     (app-bar)
     [ui/paper {:style {:width "100%"}}
      [ui/list
       (->> categories
            (map (partial list-category current-selection)))]
      ]
     ]))

(defn page []
  (let [this-page @(rf/subscribe [:page])
        page-id   (:page-id this-page)]
    [ui/mui-theme-provider
     {:mui-theme (get-mui-theme
                  {:palette
                   {:primary1-color (:primary app-theme)
                    :accent1-color  (:secondary app-theme)}
                   })}
     [:div
      (case page-id
        :home         (home-page)
        :entity-forms (entity-forms this-page)
        :list         (list-page)
        ;; default
        (home-page))]
     ]
    )
  )

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page {:page-id :home}]))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (hook-browser-navigation!)
  (mount-components))
