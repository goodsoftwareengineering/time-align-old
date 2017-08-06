(ns time-align.core
  (:require [reagent.core :as r]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [re-frame.core :as rf]
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

(def svg-consts {:viewBox      "0 0 100 100"
                 ;; :width "90" :height "90" :x "5" :y "5"
                 :cx           "50" :cy "50" :r "40"
                 :inner-r      "30"
                 :center-r     "5"
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

(defn describe-arc [cx cy r start stop]
  (let [
        p-start (utils/polar-to-cartesian cx cy r start)
        p-stop  (utils/polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- stop start) 180) "0" "1")]

    (string/join " " ["M" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 1 (:x p-stop) (:y p-stop)])))

(defn period [selected is-moving-period type period]
  (let [id          (:id period)
        start-date  (:start period)
        start-ms    (utils/get-ms start-date)
        start-angle (utils/ms-to-angle start-ms)

        stop-date  (:stop period)
        stop-ms    (utils/get-ms stop-date)
        stop-angle (utils/ms-to-angle stop-ms)

        is-period-selected (= :period
                              (get-in
                               selected
                               [:current-selection :type-or-nil]))
        selected-period    (if is-period-selected
                             (get-in
                              selected
                              [:current-selection :id-or-nil])
                             nil)

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

        mid-point-angle           (+ start-angle
                                     (/ (- stop-angle start-angle) 2))
        mid-point                 (utils/polar-to-cartesian
                                   cx cy r
                                   mid-point-angle)
        arc-length                (*
                                   (/ (- stop-angle start-angle) 360)
                                   (* 2 (.-PI js/Math) r))
        start-ms (utils/angle-to-ms start-angle)
        stop-ms (utils/angle-to-ms stop-angle)
        hour-ms utils/hour-ms
        width-stretch-ms (* 1.5 hour-ms)
        gap-stretch-ms (* 0.25 hour-ms)
        start-stretch-start-ms (- start-ms (+ width-stretch-ms gap-stretch-ms))
        start-stretch-start-angle (utils/ms-to-angle start-stretch-start-ms)
        start-stretch-stop-angle  (utils/ms-to-angle
                                   (+ start-stretch-start-ms
                                      width-stretch-ms))
        stop-stretch-start-ms (+ stop-ms gap-stretch-ms)
        stop-stretch-start-angle  (utils/ms-to-angle stop-stretch-start-ms)
        stop-stretch-stop-angle  (utils/ms-to-angle
                                  (+ stop-stretch-start-ms
                                     width-stretch-ms)) 

        arc                 (describe-arc cx cy r start-angle stop-angle)
        touch-click-handler (if
                                (not is-period-selected)
                              (fn [e]
                                (.stopPropagation e)
                                (.preventDefault e)
                                (rf/dispatch
                                 [:set-selected-period id])))
        movement-handler    (fn [e]
                              (.stopPropagation e)
                              (rf/dispatch
                               [:set-moving-period true]))]
    [:g {:key (str id)}
     [:path
      {:d            arc
       :stroke       color
       :opacity      "0.6"
       :stroke-width period-width
       :fill         "transparent"
       :onTouchStart touch-click-handler
       :onMouseDown  touch-click-handler
       }]
     (if (and is-period-selected
              (= selected-period id))
       [:g
        [:circle {:cx           (:x mid-point) :cy (:y mid-point)
                  :r            (* 0.7 (/ period-width 2))
                  :fill         "black"
                  :onTouchStart movement-handler
                  :onMouseDown  movement-handler}]
        [:path
         {:d            (describe-arc
                         cx cy r
                         start-stretch-start-angle
                         start-stretch-stop-angle)
          :stroke       "black"
          :fill         "transparent"
          :stroke-width period-width}]
        [:path
         {:d            (describe-arc
                         cx cy r
                         stop-stretch-start-angle
                         stop-stretch-stop-angle)
          :stroke       "black"
          :fill         "transparent"
          :stroke-width period-width}]
        ]
       )
     ]
    ))

(defn periods [periods selected is-moving-period]
  (let [actual  (:actual-periods periods)
        planned (:planned-periods periods)]
    [:g
     [:g
      (if (some? actual)
        (->> actual
             (map (fn [actual-period] (period selected
                                              is-moving-period
                                              :actual
                                              actual-period)))))]
     [:g
      (if (some? planned)
        (->> planned
             (map (fn [planned-period] (period selected
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
  (let [basics {:fill   "#b5b5b5"
                :stroke "#c2c2c2"
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
  (let [basics {:fill   "#d4d4d4"
                :stroke "#efefef"
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

(defn day [tasks selected day]
  (let [date-str                 (subs (.toISOString day) 0 10)
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
        on-touch-click-handler   (if selected-period
                                   (fn [e]
                                     (.preventDefault e)
                                     (rf/dispatch
                                      [:set-selected-period nil])))]

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
                  }
                 (case zoom
                   :q1 {:viewBox "0 0 50 50"}
                   :q2 {:viewBox "50 0 50 50"}
                   :q3 {:viewBox "0 50 50 50"}
                   :q4 {:viewBox "50 50 50 50"}
                   (select-keys svg-consts [:viewBox])
                   ))
     shadow-filter
     (if (nil? zoom)
       (zoom-in-buttons)
       (zoom-out-buttons)
       )
     [:circle (merge {:fill "#e8e8e8" :filter "url(#shadow-2dp)"}
                     (select-keys svg-consts [:cx :cy :r]))]
     [:circle (merge {:fill "#f1f1f1" :r (:inner-r svg-consts)}
                     (select-keys svg-consts [:cx :cy]))]
     (if selected-period
       (x-svg (merge
               (select-keys svg-consts [:cx :cy])
               {:r      (:center-r svg-consts)
                :fill   "white"
                :stroke "black"
                :shadow true
                :click  on-touch-click-handler
                })))
     (periods filtered-periods selected is-moving-period)]))

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

(defn queue [tasks selected]
  (let [periods-no-stamps (utils/filter-periods-no-stamps tasks)
        ]
    [ui/list {:style {:width "100%"}}
        (->> periods-no-stamps
             (map (fn [period]
                    [ui/list-item
                     {:style       {:width "100%"}
                      :key         (:id period)
                      :leftIcon    (r/as-element
                                    [ui/svg-icon {:viewBox "0 0 1000 1000" :style {:margin-left "0.5em"}}
                                     [:circle {:cx "500" :cy "500" :r "500" :fill (:color period)}]])
                      :primaryText (if (some? (:description period))
                                     (if (< 10 (count (:description period)))
                                       (str (string/join "" (take 10 (:description period))) " ...")
                                       (:description period))
                                     (r/as-element
                                      [:span {:style {:text-decoration "italic"
                                                      :color           "grey"}}
                                       "No period description ..."]))
                      :onTouchTap  (fn [e]
                                     (println "touched me")
                                     ;; (rf/dispatch
                                     ;;    [:set-selected-task (:task-id period)])
                                     )}])))]))

(defn home-page []
  (let [main-drawer-state @(rf/subscribe [:main-drawer-state])
        tasks             @(rf/subscribe [:tasks])
        selected          @(rf/subscribe [:selected])]

    [:div.app-container
     {:style {:display         "flex"
              :flex-wrap       "wrap"
              :justify-content "center"
              :align-content   "space-between"
              :height          "100%"
              ;; :border "yellow solid 0.1em"
              :box-sizing      "border-box"}}

     [:div.app-bar-container
      {:style {:display    "flex"
               :flex       "1 0 100%"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      [ui/app-bar {:title                    "Time Align"
                   :onLeftIconButtonTouchTap (fn [e] (rf/dispatch [:toggle-main-drawer]))}]
      [ui/drawer {:docked          false :open main-drawer-state
                  :onRequestChange (fn [new-state] (rf/dispatch [:set-main-drawer new-state]))}
       [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                      :innerDivStyle {:display "flex" :align-items "center"}}
        ;; these shit custom icons will be replaced
        [ui/svg-icon {:viewBox "0 0 1000 1000" :style {:marginRight "0.5em"}}
         [:path {:d "m 301.10485,251.13502 0,196.68543 c 0,0 298.89515,4.54175 300,0 C 450,502.3622 450,602.3622 600,648.3622 l -150,0 0,200 300,0 c -150,54 -150,154 0,200.5417 l -397.5301,2.2098 -1.10485,-203.90393 -3.31457,-194.7597 L 201.10485,652.14472 200,548.78259 200,248.75638 c 0,0 -150,3.60582 -145.874634,4.01566 4.125366,0.40983 -7.69835,-198.985158 -5.625852,-198.98022 l 249.561646,0.59466 294.9284,-1.912377 c 0.18827,66.675467 -0.25657,133.347547 -0.68787,200.020867 z"}]]
        [:span "Categories"]]
       [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                      :innerDivStyle {:display "flex" :align-items "center"}}
        [ui/svg-icon {:viewBox "0 0 1000 1000" :style {:marginRight "0.5em"}}
         [:path {:d "m 301.10485,251.13502 0,196.68543 c 0,0 397.22719,3.4369 398.33204,-1.10485 -1.10485,4.54175 -1.10485,194.54175 -1.10485,200.54175 L 450,648.3622 l 0,200 300,0 c -150,54 -150,154 0,200.5417 l -397.5301,2.2098 -1.10485,-203.90393 -3.31457,-194.7597 L 201.10485,652.14472 200,548.78259 200,248.75638 c 0,0 -150,3.60582 -145.874634,4.01566 4.125366,0.40983 -7.69835,-198.985158 -5.625852,-198.98022 l 249.561646,0.59466 294.9284,-1.912377 C 450,102.3622 450,202.3622 592.30169,252.49497 Z"}]]
        [:span "Tasks"]]
       [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                      :innerDivStyle {:display "flex" :align-items "center"}}
        [ui/svg-icon {:viewBox "0 0 1000 1000" :style {:marginRight "0.5em"}}
         [:path {:d "m 301.10485,251.13502 0,196.68543 c 0,0 397.22719,3.4369 398.33204,-1.10485 -1.10485,4.54175 -1.10485,194.54175 -1.10485,200.54175 L 450,648.3622 l 0,200 300,0 c -150,54 -150,154 0,200.5417 l -397.5301,2.2098 -1.10485,-203.90393 -3.31457,-194.7597 L 201.10485,652.14472 200,548.78259 200,248.75638 c 0,0 -150,3.60582 -145.874634,4.01566 4.125366,0.40983 -7.69835,-198.985158 -5.625852,-198.98022 l 249.561646,0.59466 294.9284,-1.912377 C 450,102.3622 450,202.3622 592.30169,252.49497 Z"}]]
        [:span "Periods"]]
       [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                      :innerDivStyle {:display "flex" :align-items "center"}}
        [ic/social-person {:style {:marginRight "0.5em"}}]
        [:span "Account"]]
       [ui/menu-item {:onTouchTap    #(rf/dispatch [:set-main-drawer false])
                      :innerDivStyle {:display "flex" :align-items "center"}}
        [ic/action-settings {:style {:marginRight "0.5em"}}]
        [:span "Settings"]]]]

     [:div.day-container
      {:style {:display    "flex"
               :flex       "1 0 100%"
               :max-height "60%"
               ;; :border "red solid 0.1em"
               :box-sizing "border-box"}
       }
      (day tasks selected (new js/Date))
      ]

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
               :z-index    "99"
               :padding    "0.75em"
               :bottom     "0"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      [ui/floating-action-button
       [ic/content-add {:color "white"}]]
      ]


     ]))

(def pages
  {:home #'home-page})

(defn page []
  [ui/mui-theme-provider
   [:div
    [(pages @(rf/subscribe [:page]))]]
   ]
  )

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

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
