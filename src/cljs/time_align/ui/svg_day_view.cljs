(ns time-align.ui.svg-day-view
  (:require [time-align.utilities :as utils]
            [time-align.client-utilities :as cutils]
            [re-frame.core :as rf]
            [time-align.history :as hist]
            [time-align.ui.common :as uic]
            [time-align.js-interop :as jsi]
            [clojure.string :as string]
            [stylefy.core :as stylefy]))

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

(stylefy/keyframes "selected-period"
                   [:0%   {:opacity "0.1"}]
                   [:50%  {:opacity "0.3"}]
                   [:100% {:opacity "0.1"}])

(stylefy/keyframes "moving-period"
                   [:0%   {:opacity "0.2" :stroke-dasharray "0, 250"}]
                   [:50%  {:opacity "0.5"}]
                   [:100% {:opacity "0.2" :stroke-dasharray "250, 0"}])

(defn period
  [selected curr-time is-moving-period type period displayed-day period-in-play]
  (let [id               (:id period)
        start-date       (:start period)
        starts-yesterday (utils/is-this-day-before-that-day?
                          start-date displayed-day)
        start-ms         (utils/get-ms start-date)
        start-angle      (if starts-yesterday
                                     0.5
                                     (cutils/ms-to-angle start-ms))

        stop-date  (:stop period)
        stops-tomorrow (utils/is-this-day-after-that-day?
                        stop-date displayed-day)
        stop-ms    (utils/get-ms stop-date)
        stop-angle (if stops-tomorrow
                     359.5
                                     (cutils/ms-to-angle stop-ms))

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

        is-planned                 (= type :planned)
        opacity                    "0.85"
        color                      (:color period)
        period-width               (js/parseInt (:period-width uic/svg-consts))
        cx                         (js/parseInt (:cx uic/svg-consts))
        cy                         (js/parseInt (:cy uic/svg-consts))
        r                          (-> (case type
                                         :actual (:r uic/svg-consts)
                                         :planned (:inner-r uic/svg-consts)
                                         (:inner-r uic/svg-consts))
                                       (js/parseFloat)
                                       (- (/ period-width 2)))

        arc                 (let [outer-r (+ r (/ period-width 2))
                                  inner-r (- r (/ period-width 2))
                                  outer-arc (uic/describe-arc
                                             cx cy outer-r start-angle stop-angle)
                                  inner-arc (uic/describe-arc-reverse
                                             cx cy inner-r stop-angle start-angle)]
                              (str outer-arc inner-arc "Z"))


        set-selected-handler (if (not is-period-selected)
                               (fn [e]
                                 (jsi/stop-propagation e)
                                 ;; I think this caused the problem on desktop
                                 ;; in mobile mode of stopping the touch/click end handler
                                 ;; from firing, which kept the inline add timeout from being canceled
                                 ;; resulting in every click event causing an inline add navigation
                                 ;; (jsi/prevent-default e)
                                 (rf/dispatch-sync
                                  [:set-selected-period id])))
        set-moving-handler   (if (and is-period-selected
                                      (= selected-period id)
                                      (not is-moving-period))
                               (fn [e]
                                 (jsi/stop-propagation e)
                                 (jsi/prevent-default e)
                                 (rf/dispatch-sync
                                  [:set-moving-period true]))
                               (if is-moving-period
                                 (fn [e]
                                   (jsi/stop-propagation e)
                                   (jsi/prevent-default e)
                                   (rf/dispatch-sync
                                    [:set-moving-period false]))))

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
     [:g
      [:path
       (merge
        ;; base with nothing selected or moving
        {:d            arc
         :opacity      opacity
         :fill         color
         :onClick      set-selected-handler}

        ;; when selected added some underneath stroke
        (when (= selected-period id)
          {:stroke       (:alternate-text-color uic/app-theme)
           :opacity ".9"
           :stroke-width "1"}))]

      ;; add overlay for animations and click event for setting moving state
      (when  (= selected-period id)
        [:g {:key (str id "-selected-and-moving-animation-stuff")}
         [:path
          (merge (stylefy/use-style
                  (merge
                   {:animation-duration (str "3s")
                    :animation-timing-function "linear"
                    :animation-iteration-count "infinite"
                    :animation-name "selected-period"}))

                 {:d                arc
                  :stroke           (:text-color uic/app-theme)
                  :stroke-width     "1"
                  :onClick          set-moving-handler
                  :fill             (:text-color uic/app-theme)})]

         (when is-moving-period
           [:path
            (merge (stylefy/use-style
                    (merge
                     {:animation-duration (str "1s")
                      :animation-timing-function "linear"
                      :animation-iteration-count "infinite"
                      :animation-name "moving-period"}))

                   {:d                arc
                    :stroke           (:primary-1-color uic/app-theme)
                    :stroke-width     "1"
                    :onClick          set-moving-handler
                    :fill             (:alternate-1-color uic/app-theme)})])])

      (when (= period-in-play id)
        (let [play-arc   (uic/describe-arc cx cy r start-angle 359)
              arc-angle (- 359 start-angle)
              arc-length (* (/ arc-angle 360) ;; percent of circle
                            (* 2 (* Math/PI r)))] ;; circumference of whole circle for this track (actual or planned)

          ;; circumference of circle at r = 40 is ~ 251.33
          ;; length of 45 deg arc on circle is ~ 31.41
          (stylefy/keyframes "playing-period"
                             [:0%   {:opacity "0.25" :stroke-dasharray (str 0 ", " arc-length )}]
                             [:100% {:opacity "0.0" :stroke-dasharray (str arc-length ", " 0)}])

          [:path (merge (stylefy/use-style
                         {:animation-duration (str "6s")
                          :animation-timing-function "linear"
                          :animation-iteration-count "infinite"
                          :animation-name "playing-period"})

                        {:d            play-arc
                         :opacity      "0.7"
                         :fill         "transparent"
                         :onClick      set-selected-handler
                         :stroke-width period-width
                         :stroke       (:text-color uic/app-theme)})]))]

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

(defn periods
  [periods selected is-moving-period curr-time displayed-day period-in-play]
  (let [
        ;; whole song and dance for putting the
        ;; selected and in play periods on _top_
        ;; TODO make this not horrible
        sel-id (get-in selected [:current-selection :id-or-nil])
        selected-period (some #(if (= sel-id (:id %)) %) periods)
        in-play-period (some #(if (= period-in-play (:id %)) %) periods)
        no-sel-periods (->> periods
                            (filter #(not= (:id %) sel-id))
                            (filter #(not= (:id %) period-in-play)))
        sel-last-periods (filter cutils/period-has-stamps
                                 (if (some? sel-id)
                                   (reverse (if (some? period-in-play)
                                              (cons in-play-period
                                                    (cons selected-period no-sel-periods))
                                              (cons selected-period no-sel-periods)))

                                   (if (some? period-in-play)
                                     (reverse (cons in-play-period
                                                    no-sel-periods))
                                     no-sel-periods)))
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
                                              displayed-day
                                              period-in-play)))))]
     [:g
      (if (some? planned)
        (->> planned
             (map (fn [planned-period] (period selected
                                               curr-time
                                               is-moving-period
                                               :planned
                                               planned-period
                                               displayed-day
                                               period-in-play)))))]]))

(defn start-touch-handler [indicator-delay day]
  (fn [elem-id ui-type e]
    (let [
          ;; figure out what time the user initially touched
          svg-coords    (cutils/client-to-view-box elem-id e ui-type)
          circle-coords (cutils/point-to-centered-circle
                         (merge (select-keys uic/svg-consts [:cx :cy])
                                svg-coords))
          angle         (cutils/point-to-angle circle-coords)
          relative-time (Math/floor (cutils/angle-to-ms angle))
          absolute-time (+ relative-time
                           (jsi/value-of (utils/zero-in-day day)))
          time-date-obj (new js/Date absolute-time)
          ;; set a function to go off after standard touch time
          ;; to start the animation and timer
          id (.setTimeout
              js/window
              (fn [_]
                (println "KICKOFF!")
                (rf/dispatch-sync
                 [:set-inline-period-long-press
                  {:press-on true
                   :start-time (new js/Date)}]))
              indicator-delay)]

      ;; (jsi/stop-propagation e)
      ;; (jsi/prevent-default e)

      ;; set the id to cancel the animation
      ;; set the time indicated initially
      (println (str "maybe starting..." id))
      (rf/dispatch-sync [:set-inline-period-long-press
                         {:timeout-id id
                          :indicator-start time-date-obj}]))))

(defn stop-touch-stop-moving-handler []
  (fn [e]
    (jsi/prevent-default e)
    (rf/dispatch-sync
     [:set-moving-period false])))

(defn stop-touch-cancel-inline-add-handler [long-press-state]
  (fn [e]
    (.clearTimeout js/window (:timeout-id long-press-state))
    (rf/dispatch-sync [:set-inline-period-long-press
                       {:indicator-start nil
                        :stop-time nil
                        :timeout-id nil
                        :press-on false}])
    (println
     (str "cancelling inline add..."
          (:timeout-id long-press-state)))))

(defn stop-touch-successful-long-press-handler
  [long-press-state
   indicator-max-duration
   indicator-duration
   indicator-arc-angle
   indicator-angle
   day]
  (fn [_]
    (println "This is where we would add")
    (let [now (jsi/value-of (new js/Date))
          started (if-let [s (:start-time long-press-state)]
                    (jsi/value-of s))

          time-held-down (if-let [started started]
                           (- now started)
                           indicator-max-duration) ;; shouldn't ever use this

          desired-period-arc-angle (if (>= time-held-down indicator-duration)
                                     indicator-arc-angle
                                     (* (/ time-held-down indicator-duration)
                                        indicator-arc-angle))
          indicator-start-angle indicator-angle
          indicator-stop-angle (.min js/Math
                                     (+ indicator-start-angle
                                        desired-period-arc-angle)
                                     359.9)
          relative-start-time  (cutils/angle-to-ms indicator-start-angle)
          relative-stop-time   (cutils/angle-to-ms indicator-stop-angle)

          absolute-start-time (+ relative-start-time
                                 (jsi/value-of (utils/zero-in-day day)))
          absolute-stop-time  (+ relative-stop-time
                                 (jsi/value-of (utils/zero-in-day day)))]

      (.log js/console {:start (new js/Date absolute-start-time)
                        :stop  (new js/Date absolute-stop-time)})

      (rf/dispatch-sync [:set-inline-period-long-press
                         {:indicator-start nil
                          :stop-time nil
                          :timeout-id nil
                          :press-on false}])

      (hist/nav! (str "/add/period?"
                      "start-time=" absolute-start-time
                      "&stop-time=" absolute-stop-time)))))

(defn svg-day-touch-handlers
  [date-str
   start-touch-click-handler
   stop-touch-click-handler
   is-moving-period]

  (let [mobile-user-agent (re-seq
                           #"(?i)Android|webOS|iPhone|iPad|iPod|BlackBerry"
                           (jsi/user-agent))]
    (merge
     (when (some? mobile-user-agent)
       {:onTouchStart (if (some? start-touch-click-handler)
                        (partial start-touch-click-handler
                                 date-str :touch))
        :onTouchEnd  stop-touch-click-handler
        :onTouchMove (if is-moving-period
                       (partial handle-period-move
                                date-str :touch))})

     (when (nil? mobile-user-agent)
       {:onMouseDown (if (some? start-touch-click-handler)
                       ;; catch the case when handler is nil otherwise partial freaks out when called
                       ;; TODO this should be moved up into the let
                       (partial start-touch-click-handler
                                date-str :mouse))
        :onMouseUp   stop-touch-click-handler
        :onMouseMove (if is-moving-period
                       (partial handle-period-move
                                date-str :mouse))}))))

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
                                     (+ (js/parseFloat
                                         (:r uic/svg-consts))
                                        (js/parseFloat
                                         (:circle-stroke uic/svg-consts)))
                                     (+ (js/parseFloat
                                         (:inner-border-r uic/svg-consts))
                                        (js/parseFloat
                                         (:circle-stroke uic/svg-consts))))
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
        indicator-delay          900 ;; slightly longer than standard touch (125ms)

        ;; this is all for figuring out how long to set the animation
        ;; relative to where the user indicated they want start for this period
        indicator-start          (:indicator-start long-press-state)
        indicator-relative-ms    (if (some? indicator-start)
                                   (utils/get-ms indicator-start))
        indicator-angle          (if (some? indicator-start)
                                   (cutils/ms-to-angle indicator-relative-ms))
        indicator-max-duration   15000
        indicator-arc-angle      (- 360 indicator-angle)
        indicator-duration       (* indicator-max-duration
                                    (/ indicator-arc-angle  360))

        start-touch-click-handler (if (and (not is-moving-period)
                                           (not (:press-on long-press-state))
                                           (nil? selected-period))
                                    (start-touch-handler indicator-delay day))

        stop-touch-click-handler  (cond
                                    is-moving-period
                                    (stop-touch-stop-moving-handler)

                                    (and (some? (:timeout-id long-press-state)) (not (:press-on long-press-state)))
                                    (stop-touch-cancel-inline-add-handler long-press-state)

                                    (:press-on long-press-state)
                                    (stop-touch-successful-long-press-handler long-press-state
                                                                              indicator-max-duration
                                                                              indicator-duration
                                                                              indicator-arc-angle
                                                                              indicator-angle
                                                                              day))

        deselect                  (fn [e]
                                    (println "deselect")
                                    (jsi/prevent-default e)
                                    (rf/dispatch-sync [:set-moving-period false])
                                    (rf/dispatch-sync [:set-selected-period nil]))

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
                   :onClick     deselect}

                  (svg-day-touch-handlers date-str
                                          start-touch-click-handler
                                          stop-touch-click-handler
                                          is-moving-period)
                  (case zoom
                    :q1 {:viewBox "40 0 55 100"}
                    :q2 {:viewBox "0 0 60 100"}
                    :q3 {:viewBox "0 40 60 60"}
                    :q4 {:viewBox "40 40 60 60"}
                    (select-keys uic/svg-consts [:viewBox])))

      [:circle (merge {:fill (:canvas-color uic/app-theme)}
                      (select-keys uic/svg-consts [:cx :cy :r]))]
      [:circle (merge {:fill "transparent"
                       :stroke (:border-color uic/app-theme)
                       :stroke-width (:circle-stroke uic/svg-consts)
                       :r  (:border-r uic/svg-consts)}
                      (select-keys uic/svg-consts [:cx :cy]))]

      (periods filtered-periods selected is-moving-period curr-time day period-in-play)

      (when display-ticker
        [:g
         [:circle {:cx (:cx uic/svg-consts) :cy (:cy uic/svg-consts) :r ".7"
                   :fill (if (some? period-in-play)
                           (:text-color uic/app-theme)
                           (:alternate-text-color uic/app-theme))
                   :stroke "transparent"}]

         [:line {:fill         "transparent"
                 :stroke-width "1.4"
                 :stroke       (if (some? period-in-play)
                                 (:text-color uic/app-theme)
                                 (:alternate-text-color uic/app-theme))
                 :stroke-linecap "butt"
                 :opacity      "1"
                 :x1           (:cx uic/svg-consts)
                 :y1           (:cy uic/svg-consts)
                 :x2           (:x ticker-pos)
                 :y2           (:y ticker-pos)}]])

      (when (and (:press-on long-press-state)
                 (nil? selected-period))
        (let [cx            (js/parseInt (:cx uic/svg-consts))
              cy            (js/parseInt (:cy uic/svg-consts))
              r             (js/parseInt (:inner-r uic/svg-consts)) ;; TODO jsi int cast integration and replace all these
              indicator-r   (/ (-> (:period-width uic/svg-consts)
                                   (js/parseInt))
                               3)
              circumference (* (* 2 (jsi/pi)) r)
              arc-length    (* circumference (/ indicator-arc-angle 360))
              point         (cutils/polar-to-cartesian cx cy r indicator-angle)
              indicator-cx  (:x point)
              period-width  (js/parseInt (:period-width uic/svg-consts))
              arc           (uic/describe-arc cx cy r indicator-angle 359)
              indicator-cy  (:y point)]

          (stylefy/keyframes "grow-indicator"
                             [:from {:stroke-dasharray (str "0, " arc-length)}]
                             [:to   {:stroke-dasharray (str arc-length " , 0")}])

          [:g

           [:path
            {:d            arc
             :stroke       (:text-color uic/app-theme)
             :opacity      "0.1"
             :stroke-width (* 1.5 period-width)
             :fill         "transparent"}]

           [:path
            (merge (stylefy/use-style
                    {:animation-duration (str (/ indicator-duration 1000) "s")
                     :animation-timing-function "linear"
                     :animation-name "grow-indicator"})
                   {:d            arc
                    :stroke       (:text-color uic/app-theme)
                    :opacity      "0.5"
                    :stroke-width (* 1.5 period-width)
                    ;; guessed and checked the dasharray
                    ;; the length of the whole circle is a little over 210
                    :stroke-dasharray (str "0 " arc-length)
                    :fill         "transparent"})]]))]]))
