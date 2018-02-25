(ns time-align.ui.queue
  (:require [time-align.client-utilities :as cutils]
            [cljs-react-material-ui.reagent :as ui]
            [reagent.core :as r]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.icons :as ic]
            [time-align.history :as hist]
            [time-align.ui.list :as list]
            [re-frame.core :as rf]
            [time-align.utilities :as utils]
            [time-align.ui.common :as uic]))

(defn queue [tasks selected]
  (let [periods-no-stamps (cutils/filter-periods-no-stamps tasks)
        sel               (:current-selection selected)
        period-selected   (= :queue (:type-or-nil sel))
        sel-id            (:id-or-nil sel)]
    [ui/list {:style {:width "100%"}}
     (->> periods-no-stamps
          (map (fn [period]
                 (list/list-item-period sel period))))]))
