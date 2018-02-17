(ns time-align.ui.svg-day-view
  (:require [time-align.utilities :as utils]
            [time-align.client-utilities :as cutils]
            [re-frame.core :as rf]
            [time-align.history :as hist]
            [time-align.ui.common :as uic]
            [time-align.js-interop :as jsi]
            [clojure.string :as string]))

(def shadow-filter
  [:defs
   [:filter {:id    "shadow-2dp"
             :x     "-50%" :y "-100%"
             :width "200%" :height "300%"}
    [:feOffset {:in "SourceAlpha" :result "offA" :dy "2"}]
    [:feOffset {:in "SourceAlpha" :result "offB" :dy "1"}]
    [:feOffset {:in "SourceAlpha" :result "offC" :dy "3"}]
    [:feMorphology {:in       "offC" :result "spreadC"
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
    [:feComposite {:in     "opA" :in2 "blurA"
                   :result "shA" :operator "in"}]
    [:feComposite {:in     "opB" :in2 "blurB"
                   :result "shB" :operator "in"}]
    [:feComposite {:in     "opC" :in2 "blurC"
                   :result "shC" :operator "in"}]
    [:feMerge
     [:feMergeNode {:in "shA"}]
     [:feMergeNode {:in "shB"}]
     [:feMergeNode {:in "shC"}]
     [:feMergeNode {:in "SourceGraphic"}]]]])

(defn handle-period-move [id type evt]
  (let [cx                (js/parseInt (:cx uic/svg-consts))
        cy                (js/parseInt (:cy uic/svg-consts))
        pos               (cutils/client-to-view-box id evt type)
        pos-t             (cutils/point-to-centered-circle
                            (merge pos {:cx cx :cy cy}))
        angle             (cutils/point-to-angle pos-t)
        mid-point-time-ms (cutils/angle-to-ms angle)]

    (rf/dispatch [:move-selected-period mid-point-time-ms])))

(defn period [selected curr-time is-moving-period type period displayed-day]
  (let [id                         (:id period)
        start-date                 (:start period)
        starts-yesterday (utils/is-this-day-before-that-day?
                          start-date displayed-day)
        start-ms                   (utils/get-ms start-date)
        start-angle                (if starts-yesterday
                                     0.5
                                     (cutils/ms-to-angle start-ms))

        stop-date  (:stop period)
        stops-tomorrow (utils/is-this-day-after-that-day?
                        stop-date displayed-day)
        stop-ms    (utils/get-ms stop-date)
        stop-angle (if stops-tomorrow
                     359.5
                                     (cutils/ms-to-angle stop-ms))

        straddles-now              (utils/straddles-now? start-date stop-date)
        now-ms                     (utils/get-ms (new js/Date))
        broken-stop-before-angle   (cutils/ms-to-angle now-ms)
        broken-start-after-angle   (cutils/ms-to-angle now-ms)

        curr-time-ms               (jsi/value-of curr-time)
        start-abs-ms               (jsi/value-of start-date)
        stop-abs-ms                (jsi/value-of stop-date)

        is-period-selected         (= :period
                                      (get-in
                                        selected
                                        [:current-selection :type-or-nil]))
        selected-period            (if is-period-selected
                                     (get-in
                                       selected
                                       [:current-selection :id-or-nil])
                                     nil)
        this-period-selected       (= selected-period id)

        opacity-minor              "0.66"
        opacity-major              ".99"
        is-planned                 (= type :planned)
        ;; actual is boldest in the past (before now)
        ;; planned is boldest in the future (after now)
        ;; opacity-before/after is used for task straddling now
        opacity-before        (if is-planned
                                opacity-minor
                                opacity-major)
        opacity-after         (if is-planned
                                opacity-major
                                opacity-minor)
        opacity                    (cond
                                     this-period-selected opacity-major

                                     ;; planned after now
                                     (and is-planned (< curr-time-ms stop-abs-ms))
                                     opacity-major

                                     ;; actual before now
                                     (and (not is-planned) (> curr-time-ms stop-abs-ms))
                                     opacity-major

                                     :else opacity-minor)

        color                      (:color period)
        period-width               (js/parseInt (:period-width uic/svg-consts))
        cx                         (js/parseInt (:cx uic/svg-consts))
        cy                         (js/parseInt (:cy uic/svg-consts))
        ;; radii need to be offset to account for path using
        ;; A (arc) command having radius as the center of path
        ;; instead of edge (like circle)
        r                          (-> (case type
                                         :actual (:r uic/svg-consts)
                                         :planned (:inner-r uic/svg-consts)
                                         (* 0.5 (:inner-r uic/svg-consts)))
                                       (js/parseInt)
                                       (- (/ period-width 2)))

        arc                        (uic/describe-arc cx cy r start-angle stop-angle)
        broken-arc-before          (uic/describe-arc cx cy r
                                                 start-angle
                                                 broken-stop-before-angle)
        broken-arc-after           (uic/describe-arc cx cy r
                                                 broken-start-after-angle
                                                 stop-angle)
        future-handle-arc          (uic/describe-arc cx cy r
                                                     (+ stop-angle
                                                        (-> 5 ;; minutes
                                                            (* 60) ;; seconds
                                                            (* 1000) ;; milleseconds
                                                            (cutils/ms-to-angle)))
                                                     (+ stop-angle
                                                        (-> 25 ;; minutes
                                                            (* 60) ;; seconds
                                                            (* 1000) ;; milleseconds
                                                            (cutils/ms-to-angle))))

        touch-click-handler        (if (not is-period-selected)
                                     (fn [e]
                                       (jsi/stop-propagation e)
                                       (jsi/prevent-default e)
                                       (rf/dispatch
                                        [:set-selected-period id])))
        movement-trigger-handler   (if (and is-period-selected
                                            (= selected-period id))
                                     (fn [e]
                                       (jsi/stop-propagation e)
                                       (rf/dispatch
                                         [:set-moving-period true])))

        yesterday-arrow-point      (cutils/polar-to-cartesian cx cy r 1)
        yesterday-arrow-point-bt   (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 3)
        yesterday-arrow-point-bb   (cutils/polar-to-cartesian
                                     cx cy (- r (* 0.7 (/ period-width 2))) 3)

        yesterday-2-arrow-point    (cutils/polar-to-cartesian cx cy r 3)
        yesterday-2-arrow-point-bt (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 5)
        yesterday-2-arrow-point-bb (cutils/polar-to-cartesian
                                     cx cy (- r (* 0.7 (/ period-width 2))) 5)

        tomorrow-arrow-point       (cutils/polar-to-cartesian cx cy r 359)
        tomorrow-arrow-point-bt    (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 357)
        tomorrow-arrow-point-bb    (cutils/polar-to-cartesian
                                     cx cy (- r (* 0.7 (/ period-width 2))) 357)

        tomorrow-2-arrow-point     (cutils/polar-to-cartesian cx cy r 357)
        tomorrow-2-arrow-point-bt  (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 355)
        tomorrow-2-arrow-point-bb  (cutils/polar-to-cartesian
                                    cx cy (- r (* 0.7 (/ period-width 2))) 355)
        prev-next-stroke "0.15"
        selected-dash-array "0.5 0.4"]


    [:g {:key (str id)}
     (if (and straddles-now ;; ticker splitting should only happen when displaying today
              (= (utils/zero-in-day displayed-day)
                 (utils/zero-in-day (new js/Date)))
              (not is-period-selected))
       ;; broken arc
       [:g
        [:path
         {:d            broken-arc-before
          :stroke       color
          :opacity      opacity-before
          :stroke-width period-width
          :fill         "transparent"
          :onClick      touch-click-handler
          :onTouchStart movement-trigger-handler
          :onMouseDown  movement-trigger-handler}]
        [:path
         {:d            broken-arc-after
          :stroke       color
          :opacity      opacity-after
          :stroke-width period-width
          :fill         "transparent"
          :onClick      touch-click-handler
          :onTouchStart movement-trigger-handler
          :onMouseDown  movement-trigger-handler}]
        (when  (= selected-period id) ;; TODO add this in the broken arc section
          [:g
           [:path
            {:d            broken-arc-before
             :stroke       (:text-color uic/app-theme)
             :stroke-dasharray selected-dash-array
             :opacity      opacity
             :stroke-width (* 1.1 period-width)
             :fill         "transparent"}]
           [:path
            {:d            broken-arc-after
             :stroke       (:text-color uic/app-theme)
             :stroke-dasharray selected-dash-array
             :opacity      opacity
             :stroke-width (* 1.1 period-width)
             :fill         "transparent"}]])]

       ;; solid arc
       [:g
        [:path
         {:d            arc
          :stroke       color
          :opacity      opacity
          :stroke-width period-width
          :fill         "transparent"
          :onClick      touch-click-handler
          :onTouchStart movement-trigger-handler
          :onMouseDown  movement-trigger-handler}]
        (when  (= selected-period id) ;; TODO add this in the broken arc section
          [:g
           [:path
            {:d            arc
             :stroke       (:text-color uic/app-theme)
             :stroke-dasharray selected-dash-array
             :opacity      "0.7"
             :stroke-width  period-width
             :fill         "transparent"}]])])

     ;; yesterday arrows TODO change all yesterdays and tomorrows to next and previous days
     (if starts-yesterday
       [:g
        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                     (:x yesterday-arrow-point-bt) ","
                                     (:y yesterday-arrow-point-bt) " "
                                     (:x yesterday-arrow-point) ","
                                     (:y yesterday-arrow-point) " "
                                     (:x yesterday-arrow-point-bb) ","
                                     (:y yesterday-arrow-point-bb) " ")}]

        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                     (:x yesterday-2-arrow-point-bt) ","
                                     (:y yesterday-2-arrow-point-bt) " "
                                     (:x yesterday-2-arrow-point) ","
                                     (:y yesterday-2-arrow-point) " "
                                     (:x yesterday-2-arrow-point-bb) ","
                                     (:y yesterday-2-arrow-point-bb) " ")}]])

     ;; tomorrow arrows
     (if stops-tomorrow
       [:g
        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                     (:x tomorrow-arrow-point-bt) ","
                                      (:y tomorrow-arrow-point-bt) " "
                                      (:x tomorrow-arrow-point) ","
                                      (:y tomorrow-arrow-point) " "
                                      (:x tomorrow-arrow-point-bb) ","
                                      (:y tomorrow-arrow-point-bb) " ")}]

        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                      (:x tomorrow-2-arrow-point-bt) ","
                                      (:y tomorrow-2-arrow-point-bt) " "
                                      (:x tomorrow-2-arrow-point) ","
                                      (:y tomorrow-2-arrow-point) " "
                                      (:x tomorrow-2-arrow-point-bb) ","
                                      (:y tomorrow-2-arrow-point-bb) " ")}]])]))

(defn periods [periods selected is-moving-period curr-time displayed-day]
  (let [
        ;; whole song and dance for putting the selected period on _top_
        sel-id (get-in selected [:current-selection :id-or-nil])
        selected-period (some #(if (= sel-id (:id %)) %) periods)
        no-sel-periods (filter #(not= (:id %) sel-id) periods)
        sel-last-periods (if (some? sel-id)
                           (reverse (cons selected-period no-sel-periods))
                           periods)
        ;; dance done
        actual (filter #(not (:planned %)) sel-last-periods)
        planned (filter #(:planned %) sel-last-periods)
        selected-planned (:planned selected-period)] ;; TODO fuse these and have them use the :planned flag in periods
    [:g
     [:g
      (if (some? actual)
        (->> actual
             (map (fn [actual-period] (period selected
                                              curr-time
                                              is-moving-period
                                              :actual
                                              actual-period
                                              displayed-day)))))]
     [:g
      (if (some? planned)
        (->> planned
             (map (fn [planned-period] (period selected
                                               curr-time
                                               is-moving-period
                                               :planned
                                               planned-period
                                               displayed-day))))
        )]]))

(defn day [tasks selected day]
  (let [date-str                 (subs (jsi/->iso-string day) 0 10)
        curr-time                (:time @uic/clock-state)
        period-in-play           @(rf/subscribe [:period-in-play])
        display-ticker           (= (jsi/value-of (utils/zero-in-day day))
                                    (jsi/value-of (utils/zero-in-day curr-time)))

        ticker-ms                (utils/get-ms curr-time)
        ticker-angle             (cutils/ms-to-angle ticker-ms)
        ticker-pos               (cutils/polar-to-cartesian
                                   (:cx uic/svg-consts)
                                   (:cy uic/svg-consts)
                                   (if (some? period-in-play)
                                     (:r uic/svg-consts)
                                     (- (:inner-r uic/svg-consts)
                                        (:period-width uic/svg-consts)))
                                   ticker-angle)
        filtered-periods         (cutils/filter-periods-for-day day tasks)
        selected-period          (if (= :period
                                        (get-in
                                          selected
                                          [:current-selection :type-or-nil]))
                                   (get-in selected
                                           [:current-selection :id-or-nil])
                                   nil)
        is-moving-period         @(rf/subscribe [:is-moving-period])
        period-in-play-color     @(rf/subscribe [:period-in-play-color])
        long-press-state         @(rf/subscribe [:inline-period-long-press])
        ;; start touch sets timeout callback
        ;; passes id to view state along with time stamp
        ;; when view state on set start stop to abort timeout callback using id from view
        start-touch-click-handler (if (and (not is-moving-period)
                                           (not (:press-on long-press-state)))
                                    (fn [elem-id ui-type e]
                                      (let [id (.setTimeout
                                                js/window
                                                (fn [_]
                                                  (println "start the inline period add!")
                                                  (rf/dispatch [:set-inline-period-add-dialog
                                                                true]))
                                                700)
                                            svg-coords    (cutils/client-to-view-box elem-id e ui-type)
                                            circle-coords (cutils/point-to-centered-circle
                                                           (merge (select-keys uic/svg-consts [:cx :cy])
                                                                  svg-coords))
                                            angle         (cutils/point-to-angle circle-coords)
                                            relative-time (Math/floor (cutils/angle-to-ms angle))
                                            absolute-time (+ relative-time
                                                             (jsi/value-of (utils/zero-in-day day)))
                                            time-date-obj (new js/Date absolute-time)]

                                        (rf/dispatch [:set-inline-period-long-press
                                                      {:press-time time-date-obj
                                                       :callback-id id
                                                       :press-on true}]))))
        stop-touch-click-handler  (if is-moving-period
                                   (fn [e]
                                     (jsi/prevent-default e)
                                     (rf/dispatch
                                      [:set-moving-period false]))
                                   (if (:press-on long-press-state)
                                     (fn [e]
                                       (println "stopping long press")
                                       (.clearTimeout js/window (:callback-id long-press-state))
                                       (rf/dispatch [:set-inline-period-long-press
                                                     {:press-time nil
                                                      :callback-id nil
                                                      :press-on false}]))))
        deselect                  (if (not is-moving-period)
                                   (fn [e]
                                     (jsi/prevent-default e)
                                     (rf/dispatch
                                      [:set-selected-period nil])))
        zoom @(rf/subscribe [:zoom])]

    [:div {:style {:height "100%" :width "100%"}}
     [:svg (merge {:key         date-str
                   :id          date-str
                   :xmlns "http://www.w3.org/2000/svg"
                   :version  "1.1"
                   :style       {
                                 :display      "inline-box"
                                 ;; this stops scrolling
                                 :touch-action "pinch-zoom"
                                 ;; for moving period
                                 }
                   :width       "100%"
                   :height      "100%"
                   :onMouseDown (if (some? start-touch-click-handler) ;; catch the case when handler is nil otherwise partial freaks out when called
                                                                ;; TODO this should be moved up into the let
                                  (partial start-touch-click-handler
                                           date-str :mouse))
                   :onTouchStart (if (some? start-touch-click-handler)
                                   (partial start-touch-click-handler
                                            date-str :touch))

                   :onTouchEnd  stop-touch-click-handler
                   :onMouseUp   stop-touch-click-handler

                   :onTouchMove (if is-moving-period
                                  (partial handle-period-move
                                           date-str :touch))
                   :onMouseMove (if is-moving-period
                                  (partial handle-period-move
                                           date-str :mouse))
                   :onClick     deselect}
                  (case zoom
                    :q1 {:viewBox "40 0 60 60"}
                    :q2 {:viewBox "0 0 60 60"}
                    :q3 {:viewBox "0 40 60 60"}
                    :q4 {:viewBox "40 40 60 60"}
                    (select-keys uic/svg-consts [:viewBox])))

      shadow-filter
      [:circle (merge {:fill (:canvas-color uic/app-theme)
                       ;; :stroke (:border-color uic/app-theme)
                       ;; :filter "url(#shadow-2dp)"
                       }
                      (select-keys uic/svg-consts [:cx :cy :r]))]
      [:circle (merge {:fill (:canvas-color uic/app-theme)
                       :stroke (:border-color uic/app-theme)
                       :stroke-width "0.25"
                       :r (:inner-r uic/svg-consts)}
                      (select-keys uic/svg-consts [:cx :cy]))]
      [:circle (merge {:fill (:canvas-color uic/app-theme)
                       :stroke (:border-color uic/app-theme)
                       :stroke-width "0.25"
                       :r (- (:inner-r uic/svg-consts)
                             (:period-width uic/svg-consts))}
                      (select-keys uic/svg-consts [:cx :cy]))]
      (when display-ticker
        [:g
         [:circle {:cx (:cx uic/svg-consts) :cy (:cy uic/svg-consts)
                   :r ".7"
                   :fill (if (some? period-in-play)
                           period-in-play-color
                           (:text-color uic/app-theme))
                   :stroke "transparent"}]
         [:line {:fill         "transparent"
                 :stroke-width "1.4"
                 :stroke       (if (some? period-in-play)
                                 period-in-play-color
                                 "white")
                 :stroke-linecap "butt"
                 :opacity      "1"
                 ;; :filter       "url(#shadow-2dp)"
                 ;; filter breaks bounding box and results in zero width or height
                 ;; on vertical and horizontal lines (6, 9, 12, 0)
                 :x1           (:cx uic/svg-consts)
                 :y1           (:cy uic/svg-consts)
                 :x2           (:x ticker-pos)
                 :y2           (:y ticker-pos)}]])
      (periods filtered-periods selected is-moving-period curr-time day)]]))

(defn days [days tasks selected-period]
  (->> days
       (map (partial day tasks selected-period))))
