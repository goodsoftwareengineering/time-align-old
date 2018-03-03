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

(def basic-button {:style {}})

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

(defn edit-button [id & {:keys [type] :or {type :period}}]
  [ui/floating-action-button
   (merge basic-button
          {:secondary true}
          {:onTouchTap
           (fn [_]
             (hist/nav! (str "/edit/" (name type) "/" id)))})
   [ic/image-edit uic/basic-ic]])

(defn add-button [add-fn]
  [ui/floating-action-button
   (merge basic-button
          {:onTouchTap add-fn})
   [ic/content-add uic/basic-ic]])

(defn copy-button [copy-fn]
  [ui/floating-action-button
   (merge basic-button
          {:onTouchTap copy-fn})
   [ic/content-content-copy uic/basic-ic]])

(defn action-buttons-period-selection [period-in-play id copy-fn]
  [:div {:style {:display "flex"
                 :flex-direction "column"}}
   (edit-button id)
   [:div {:style {:padding "0.25em"}}] ;; TODO get rid of this style hack pad
   (copy-button copy-fn)
   [:div {:style {:padding "0.25em"}}] ;; TODO get rid of this style hack pad
   (if (some? period-in-play) (pause-button) (play-button))])

(defn action-buttons-pause [selected period-in-play]
  [:div (pause-button)])

(defn action-buttons-add [add-fn]
  [:div {:style {:display "flex"
                 :flex-direction "column"}}
   (add-button add-fn)])
