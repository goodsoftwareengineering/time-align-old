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

(def svg-consts {:viewBox "0 0 100 100"
                 :width "90" :height "90" :x "5" :y "5"
                 :cx "50" :cy "50" :r "40" :inner-r "30"})

(def shadow-filter
  [:defs
   [:filter {:id "shadow-2dp"
             :x "-50%" :y "-100%"
             :width "200%" :height "300%"}
    [:feOffset {:in "SourceAlpha" :result "offA" :dy "2"}]
    [:feOffset {:in "SourceAlpha" :result "offB" :dy "1"}]
    [:feOffset {:in "SourceAlpha" :result "offC" :dy "3"}]
    [:feMorphology {:in "offC" :result "spreadC"
                    :operator "erode" :radius "2"}]
    [:feGaussianBlur {:in "offA" :result "blurA"
                      :stdDeviation "1"}]
    [:feGaussianBlur {:in "offB" :result "blurB"
                      :stdDeviation "2.5"}]
    [:feGaussianBlur {:in "spreadC" :result "blurC"
                      :stdDeviation "0.5"}]
    [:feFlood {:flood-opacity "0.14" :result "opA"}]
    [:feFlood {:flood-opacity "0.12" :result "opB"}]
    [:feFlood {:flood-opacity "0.20" :result "opC"}]
    [:feComposite {:in "opA" :in2 "blurA"
                   :result "shA" :operator "in"}]
    [:feComposite {:in "opB" :in2 "blurB"
                   :result "shB" :operator "in"}]
    [:feComposite {:in "opC" :in2 "blurC"
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

(defn render-period [selected-period period]
  (let [id (:id period)
        start-date (:start period)
        start-ms (utils/get-ms start-date)
        start-angle (utils/ms-to-angle start-ms)

        stop-date (:stop period)
        stop-ms (utils/get-ms stop-date)
        stop-angle (utils/ms-to-angle stop-ms)

        type (:type period)
        color (cond
                ;; actual
                (and (or (nil? selected-period)
                         (= selected-period id))
                     (= :actual type))
                "#43a047"
                ;; planned
                (and (or (nil? selected-period)
                         (= selected-period id))
                     (= :planned type))
                "#63ccff"
                ;; something else selected
                :else (if (= :planned type)
                        "#aaaaaa"
                        "#a1a1a1"))

        arc (describe-arc 50 50
                          (if (= :actual type) 35 25)
                          start-angle stop-angle)]

    [:path
     {:key (str id)
      :d arc
      :stroke color
      :opacity "0.5"
      :stroke-width "10"
      :fill "transparent"
      :onClick (if (nil? selected-period)
                 (fn [e]
                   (.stopPropagation e)
                   (rf/dispatch
                    [:set-selected-period id])))}]))

(defn render-periods [col-of-col-of-periods selected-period]
  (->> col-of-col-of-periods
       (map (fn [periods]
              (->> periods
                   (map (partial render-period selected-period)))))))

(defn convert-client-to-view-box [id evt]
  (let [pt (-> (.getElementById js/document id)
               (.createSVGPoint))
        ctm (-> evt
                (.-target)
                (.getScreenCTM))]

    (set! (.-x pt) (.-clientX evt))
    (set! (.-y pt) (.-clientY evt))

    (let [trans-pt (.matrixTransform pt (.inverse ctm))]
      {:x (.-x trans-pt) :y (.-y trans-pt)})))

(defn convert-point-to-angle
  ;; expects map {:x number :y number}
  ;; in the form of circle centered cartesian coords
  [{:keys [x y]}]

  (let [pi (.-PI js/Math)
        xa (.abs js/Math x)
        ya (.abs js/Math y)
        quadrant (cond
                   (and (> x 0) (> y 0)) 1
                   (and (> x 0) (< y 0)) 2
                   (and (< x 0) (< y 0)) 3
                   (and (< x 0) (> y 0)) 4
                   :else 0)
        special (cond
                  (and (= x 0) (> y 0)) 0
                  (and (> x 0) (= y 0)) (-> pi (/ 2))
                  (and (= x 0) (< y 0)) pi
                  (and (< x 0) (= y 0)) (-> pi (/ 2) (* 3))
                  :else nil)
        angle-in-radians (if (some? special)
                           special
                           (case quadrant
                             1 (.atan js/Math (/ xa ya))
                             2 (-> (.atan js/Math (/ ya xa)) (+ (/ pi 2)))
                             3 (-> (.atan js/Math (/ xa ya)) (+ pi))
                             4 (-> (.atan js/Math (-> (/ ya xa))) (+ (-> pi (/ 2) (* 3))))
                             0))]

    (/ (* angle-in-radians 180) pi)))

(defn handle-period-move [id evt]
  (.log js/console "--------------")
  (let [
        pos (convert-client-to-view-box id evt)

        ;; viewbox coords to cartesian circle centered
        x (:x pos)
        y (:y pos)
        cx (js/parseInt (:cx svg-consts))
        cy (js/parseInt (:cy svg-consts))
        xt (- x cx)
        yt (if (>= y cy)
             (- 0 (- y cy))
             (- cy y))

        angle (convert-point-to-angle {:x xt :y yt})
        time-ms (utils/angle-to-ms angle)
        ]

    (.log js/console pos)
    (.log js/console {:xt xt :yt yt})
    (.log js/console angle)
    (.log js/console (-> time-ms
                         (/ 1000)
                         (/ 60)
                         (/ 60)))
  ;;   (rf/dispatch [:set-selected-period-start time])
  ))

(defn render-day [tasks selected-period day]
  (let [date-str (subs (.toISOString day) 0 10)
        col-of-col-of-periods (utils/filter-periods day tasks)]

    [:svg (merge {:key date-str
                  :id date-str
                  :style {:display "inline-box"}
                  :width "100%"
                  :height "600px"
                  :onMouseMove (if (not (nil? selected-period))
                                 (partial handle-period-move date-str))}
                 (select-keys svg-consts [:viewBox]))
     shadow-filter
     [:circle (merge {:fill "#e8e8e8" :filter "url(#shadow-2dp)"}
                     (select-keys svg-consts [:cx :cy :r]))]
     [:circle (merge {:fill "#f1f1f1" :r (:inner-r svg-consts)}
                     (select-keys svg-consts [:cx :cy]))]
     (render-periods col-of-col-of-periods selected-period)]))

(defn render-days [days tasks selected-period]
  (->> days
       (map (partial render-day tasks selected-period))))

(defn home-page []
  (let [tasks @(rf/subscribe [:tasks])
        queue @(rf/subscribe [:queue])
        days  @(rf/subscribe [:visible-days])
        selected-period @(rf/subscribe [:selected-period])]

    [:div
     {:style {:display "flex" :justify-content "flex-start"
              :flex-wrap "no-wrap"}
      :onClick (fn [e] (rf/dispatch [:set-selected-period nil]))}
     (render-days days tasks selected-period)]))

(def pages
  {:home #'home-page})

(defn page []
  [:div
   [(pages @(rf/subscribe [:page]))]])

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
