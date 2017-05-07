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

(defn describe-arc [cx cy r start stop]
  (let [
        p-start (utils/polar-to-cartesian cx cy r start)
        p-stop  (utils/polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- stop start) 180) "0" "1")]

    (string/join " " ["M" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 1 (:x p-stop) (:y p-stop)])))


(defn render-periods [col-of-col-of-periods]
  (->> col-of-col-of-periods
       (map (fn [periods]
              (->> periods
                   (map #(let [id (:task-id %)
                               start-date (:start %)
                               start-ms (utils/get-ms start-date)
                               start-angle (utils/ms-to-angle start-ms)

                               stop-date (:stop %)
                               stop-ms (utils/get-ms stop-date)
                               stop-angle (utils/ms-to-angle stop-ms)

                               arc (describe-arc 50 50 30 start-angle stop-angle)]

                           [:path {:key (str id "-" start-ms "-" stop-ms)
                                   :d arc
                                   :stroke "black"
                                   :stroke-width "4"
                                   :fill "transparent"}])))))))

(defn render-days [days tasks]
  (->> days
       (map (fn [day]
              (let [date-str (.toDateString day)
                    ms (utils/get-ms day)
                    angle (utils/ms-to-angle ms)
                    col-of-col-of-periods (utils/filter-periods day tasks)]

                [:svg {:key date-str :style {:display "inline-box"}
                       :width "100%" :viewBox "0 0 100 100"}
                 [:circle {:cx "50" :cy "50" :r "30"
                           :fill "grey"}]
                 (render-periods col-of-col-of-periods)])))))

(defn selection-tools []
  (let [icon-style {:margin "0.5em"}]
    [:div {:class "selection-tools"
           :style {:display "flex"
                   :justify-content "center"
                   :flex-wrap "nowrap"
                   :align-content "flex-start"
                   :margin-bottom "1em"
                   :margin-top "1em"}}
     [:i {:class "material-icons" :style icon-style
          :onClick #(rf/dispatch [:set-view-range-day])} "view_day"]
     [:i {:class "material-icons" :style icon-style
          :onClick #(rf/dispatch [:set-view-range-week])} "view_week"]
     [:i {:class "material-icons" :style icon-style
          :onClick #(rf/dispatch [:set-view-range-week
                                  {:start (new js/Date)
                                   :stop (new js/Date)}])} "date_range"]]))

(defn home-page []
  (let [tasks @(rf/subscribe [:tasks])
        drawer-state (rf/subscribe [:drawer])
        days  @(rf/subscribe [:visible-days])]

    (pprint {:days days :tasks tasks})

    [ui/mui-theme-provider
     {:mui-theme (get-mui-theme {:palette {:text-color (color :blue200)}})}
     [:div
      [ui/drawer {:open @drawer-state
                  :docked false
                  :on-request-change #(rf/dispatch [:set-drawer-state %])}]
      [ui/paper
       [ui/raised-button {:label "Blue button"
                          :on-click #(rf/dispatch [:set-drawer-state true])}]

       (selection-tools)
       [:div
        {:style {:display "flex" :justify-content "flex-start"
                 :flex-wrap "no-wrap"}}
        (render-days days tasks)]]]]))

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
