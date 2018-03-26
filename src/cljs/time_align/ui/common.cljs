(ns time-align.ui.common
  (:require [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [reagent.core :as r]
            [camel-snake-kebab.core :refer [->kebab-case]]
            [re-frame.core :as rf]
            [time-align.client-utilities :as cutils]
            [clojure.string :as string]
            [goog.string :as gstring]
            [time-align.utilities :as utils]))

(defonce clock-state (r/atom {:time (new js/Date)}))

(def span-style-ellipsis-one-line {:overflow      "hidden"
                                   :white-space   "nowrap"
                                   :max-width     "100%"
                                   :text-overflow "ellipsis"
                                   :display       "inline-block"})

(defn describe-arc [cx cy r start stop]
  (let [
        p-start        (cutils/polar-to-cartesian cx cy r start)
        p-stop         (cutils/polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- stop start) 180) "0" "1")]

    (string/join " " ["M" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 1 (:x p-stop) (:y p-stop)])))

(defn describe-arc-reverse [cx cy r start stop]
  (let [
        p-start        (cutils/polar-to-cartesian cx cy r start)
        p-stop         (cutils/polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- start stop) 180) "0" "1")]

    (string/join " " ["L" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 0 (:x p-stop) (:y p-stop)])))

(def basic-ic {:style {:marginTop "7.5px"}
               :color "white"})

(defn duration-ms-to-string [time]
  (str
    (gstring/format "%.2f"
                    (-> time
                        (/ 1000)
                        (/ 60)
                        (/ 60)))
    " hours"))

(defn clock-tick []
  (swap! clock-state assoc :time (new js/Date))

  (if (some? (get-in @re-frame.db/app-db [:view :period-in-play])) ;; TODO this smells bad
    (rf/dispatch [:update-period-in-play])
    )
  ;; TODO think about putting clock state in db
  ;; have handler that ticks the clock + resets any "playing" period
  )

;; TODO def a colors.co url that is default nil
;; TODO defn use-colors-co-url-if-present that
;; takes in the output of conver-js-mui-theme
;; and replaces canvas, primaries, and accents using order of colors in url
;; make sure to search the original values cavnas, primaries, and accents and replace them with new colors
;; return the original map if url is nil or isn't parsable
;; This will allow for very quick testing of color schemes
;; compiling different versions of the app to AB test will also be easy, they can all just point to the same DB
;; - https://coolors.co/fefeff-d6efff-fed18c-fed99b-fe654f
;; - https://coolors.co/0f1108-190933-acfcd9-eb5e55-d81e5b
;; - https://coolors.co/0f1108-25332d-acfcd9-665c77-e3e4db
;; - https://coolors.co/e4fde1-8acb88-648381-575761-ffbf46

;; from here https://cimdalli.github.io/mui-theme-generator/
;; set in home.html template
(def json-mui-theme-palette  (.-mui_theme_palette js/window))
(def json-mui-theme-overides (.-mui_theme_overides js/window))

(defn convert-js-mui-theme [obj]
  (->> obj
       (js->clj)
       (seq)
       (reduce
        (fn [m [k v]]
          (merge m {(-> k
                        (->kebab-case)
                        (keyword))
                    v})) {})))

(def converted-js-mui-theme-palette (convert-js-mui-theme json-mui-theme-palette))
(def app-theme (merge converted-js-mui-theme-palette
                      {;; TODO refactor primary & secondary
                       :primary   (:primary-1-color converted-js-mui-theme-palette)
                       :secondary (:accent-1-color converted-js-mui-theme-palette)}))

(def app-theme-with-component-overides
  (merge {:palette app-theme}
         (convert-js-mui-theme json-mui-theme-overides)))

(defn mini-arc [period]
  (let [{:keys [id description color planned]} period
        start                          (:start period)
        start-ms                       (utils/get-ms start)
        start-angle                    (cutils/ms-to-angle start-ms)
        stop                           (:stop period)
        stop-ms                        (utils/get-ms stop)
        stop-angle                     (cutils/ms-to-angle stop-ms)

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

    [ui/svg-icon
     {:viewBox "0 0 41 41"}
     [:g
      [:circle {:cx      "20.5" :cy "20.5" :r "20"
                :opacity "0.66"
                                :fill    (:border-color app-theme)}]
      [:circle {:cx      "20.5" :cy "20.5" :r "10"
                :opacity "0.66"
                :fill    (:border-color app-theme)}]
      [:circle {:cx      "20.5" :cy "20.5"
                :r (if planned "8" "18")
                :opacity "1"
                :stroke  color
                :stroke-width "4"
                :fill    "transparent"}]

      ;; [:path
      ;;  {:d            (describe-arc 20.5 20.5
      ;;                               (if planned 5 15)
      ;;                               start-used stop-used)
      ;;   :stroke       color
      ;;   :stroke-width "5"
      ;;   :fill         "transparent"}]
      ]]))

(def svg-consts {:viewBox        "0 0 100 100"
                 ;; :width "90" :height "90" :x "5" :y "5"
                 :cx             "50" :cy "50"
                 :r              "45"
                 :inner-r        "34"
                 :ticker-r       "5"
                 :center-r       "5"  ;; TODO might not be used
                 :circle-stroke  "0.25"
                 :period-width   "10"
                 :border-r       "34.5"
                 :inner-border-r "23.5"})

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
            :fill         "transparent"}]]])

(defn svg-mui-circle [{:keys [color style]}]
  [ui/svg-icon
   {:viewBox "0 0 24 24" :style (merge {:margin "0"} style)}
   [:circle {:cx "12" :cy "12" :r "11" :fill color}]])

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

(defn concatenated-text
  "Takes a text message and a fall back message.
   Returns a span element with ellispsis cutt off that fits the available width."
  [text empty-text]
  (let [some-text (and (some? text)
                       (not (empty? text)))]
    [:span {:style
            (merge span-style-ellipsis-one-line
                   (when (not some-text) {:color (:secondary-text app-theme)}))}
     (if some-text
       text
       empty-text)]))

(defn period-list-item-primary-text
  "takes in a period and gives back a string to use as info in a list item element"
  [period]

  (let [description (:description period)]
    (r/as-element (concatenated-text description "..."))))

(defn period-list-item-secondary-text
  [period]
  (let [duration-ms (- (:stop period) (:start period))
        has-stamps (cutils/period-has-stamps period)]
    (if has-stamps
      (str (utils/date-string (:start period))
           " : "
           (duration-ms-to-string duration-ms))
      "Queue item")))
