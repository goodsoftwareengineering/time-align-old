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

(defn concatenated-text
  "Takes a text message, a cut off character limit, and a fall back message. Returns text concatonated with ellipsis, full text if it is less than 10 characters or an (r/element) styled grey if text is empty."
  [text character-limit if-empty-message]
  (if (and (some? text)
           (not (empty? text)))
    (if (< character-limit (count text))
      (str (string/join "" (take character-limit text)) " ...")
      text)
    ;; returns empty message as an r/as-emelent because that is the only way to style
    ;; text in a listem item primary-text attr
    (r/as-element
      [:span {:style {:text-decoration "italic"
                      :color           "grey"}}
       if-empty-message])))

(defn period-list-item-primary-text
  "takes in a period and gives back a string to use as info in a list item element"
  [period]

  (let [description (:description period)]
    (concatenated-text description 20 "no description...")))

(defn period-list-item-secondary-text
  [period]
  (let [duration-ms (- (:stop period) (:start period))
        has-stamps (cutils/period-has-stamps period)]
    (if has-stamps
      (str (utils/date-string (:start period))
           " : "
           (duration-ms-to-string duration-ms))
      "Queue item"
      )))

(defn clock-tick []
  (swap! clock-state assoc :time (new js/Date))

  (if (some? (get-in @re-frame.db/app-db [:view :period-in-play])) ;; TODO this smells bad
    (rf/dispatch [:update-period-in-play])
    )
  ;; TODO think about putting clock state in db
  ;; have handler that ticks the clock + resets any "playing" period
  )

;; from here https://cimdalli.github.io/mui-theme-generator/
;; take export
;; JSON.stringinfy(export)
;; /s/"/\\"/g
;; surround with final "
;; paste here
(def json-string-mui-theme-palette
  (str "{\"primary1Color\":\"#607d8b\",\"primary2Color\":\"#78909c\",\"primary3Color\":\"#90a4ae\",\"accent1Color\":\"#e91e63\",\"accent2Color\":\"#d81b60\",\"accent3Color\":\"#90a4ae\",\"alternateTextColor\":\"#cfd8dc\",\"secondaryTextColor\":\"#607d8b\",\"textColor\":\"#ffffff\",\"canvasColor\":\"#263238\",\"borderColor\":\"#cfd8dc\",\"disabledColor\":\"#b0bec5\",\"pickerHeaderColor\":\"#90a4ae\",\"clockCircleColor\":\"#607d8b\",\"shadowColor\":\"#212121\"}"))
(def json-string-mui-theme-overides
  (str "{\"snackbar\":{\"backgroundColor\":\"#546e7a\",\"actionColor\":\"#263238\"},\"raisedButton\":{\"color\":\"#546e7a\",\"disabledColor\":\"#455a64\"},\"tableRow\":{\"selectedColor\":\"#455a64\"},\"toggle\":{\"thumbOffColor\":\"#455a64\",\"thumbOnColor\":\"#90a4ae\",\"trackOnColor\":\"rgba(120, 144, 156, 0.5)\",\"trackDisabledColor\":\"#37474f\",\"thumbDisabledColor\":\"#37474f\",\"trackOffColor\":\"#455a64\",\"thumbRequiredColor\":\"#78909c\"},\"slider\":{\"trackColor\":\"#607d8b\",\"trackColorSelected\":\"#b0bec5\",\"selectionColor\":\"#e91e63\"},\"timePicker\":{\"clockColor\":\"#37474f\",\"clockCircleColor\":\"#455a64\",\"headerColor\":\"#546e7a\"},\"textField\":{\"textColor\":\"#ffffff\",\"focusColor\":\"#ffffff\"},\"datePicker\":{\"selectColor\":\"#546e7a\",\"textColor\":\"#eceff1\"}}" ))

(defn convert-js-mui-theme [string]
  (->> string
       (.parse js/JSON)
       (js->clj)
       (seq)
       (reduce
        (fn [m [k v]]
          (merge m {(-> k
                        (->kebab-case)
                        (keyword))
                    v})) {})))

(def converted-js-mui-theme-palette (convert-js-mui-theme json-string-mui-theme-palette))
(def app-theme (merge converted-js-mui-theme-palette
                      {;; TODO refactor primary & secondary
                       :primary   (:primary-1-color converted-js-mui-theme-palette)
                       :secondary (:accent-1-color converted-js-mui-theme-palette)}))

(def app-theme-with-component-overides
  (merge {:palette app-theme}
         (convert-js-mui-theme json-string-mui-theme-overides)))

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
            :fill         "transparent"}]
    ]
   ]
  )

(defn svg-mui-circle [color]
  [ui/svg-icon
   {:viewBox "0 0 24 24" :style {:margin-left "0.5em"}}
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
