(ns time-align.ui.list
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.ui.common :as uic]
            [re-frame.core :as rf]
            [time-align.history :as hist]
            [cljs-react-material-ui.icons :as ic]
            [time-align.client-utilities :as cutils]
            [time-align.utilities :as utils]
            [time-align.js-interop :as jsi]))



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
              :primaryText (uic/period-list-item-primary-text period)
              :secondaryText (uic/period-list-item-secondary-text period)
              :style       (if is-selected {:backgroundColor "#ededed"})
               :onClick     (fn [e]
                              (when-not is-selected
                                (rf/dispatch [:set-selected-period id])))

               :on-double-click (fn [e]
                                  (when is-selected
                                    (hist/nav! (str "/edit/period/" id))))

               }

              (if (and (some? (:start period))
                       (some? (:stop period)))

                ;; if not queue render the arc
                {:leftIcon (r/as-element (uic/mini-arc period))}

                ;; otherwise render a queue indicator
                {:leftIcon (r/as-element
                             [ui/svg-icon [ic/action-list {:color color}]])}))])))

(defn list-task [current-selection task]
  (let [{:keys [id name periods complete color]} task

        periods            periods
        periods-with-color (->> periods (map #(assoc % :color color)))
        periods-sorted     (reverse
                             (sort-by #(if (some? (:start %))
                                         (jsi/value-of (:start %))
                                         0) periods-with-color))
        sel-id             (:id-or-nil current-selection)
        sel-cat            (:type-or-nil current-selection)
        is-selected        (and (= :task sel-cat)
                                (= id sel-id))
        is-child-selected  (->> periods
                                (some #(if (= sel-id (:id %)) true nil))
                                (some?))
        children (into [(r/as-element
                         [ui/raised-button {:key (str "add-period-for-task-" id)
                                            :href "#/add/period"
                                            :label "Add Period"
                                            :background-color "grey"
                                            :style {:margin-top "1em"
                                                    :margin-left "3em"
                                                    :margin-bottom "1em"}}])]
                       (->> periods-sorted
                            (map (partial list-period current-selection))))]
    (r/as-element
      [ui/list-item
       {:key           id
        :primaryText   (uic/concatenated-text name 15 "no name entered ...")
        :nestedItems   children
        :leftIcon      (r/as-element
                         [ui/checkbox {:checked   complete
                                       :iconStyle {:fill color}}])
        :open          (or is-selected
                           is-child-selected)
        :style         (if is-selected {:backgroundColor "#ededed"})


        :onClick       (fn [e]
                         (rf/dispatch [:set-selected-task id]))

        :onDoubleClick (fn [e]
                         (when is-selected
                           (hist/nav! (str "/edit/task/" id))))

        }])))

(defn list-category [current-selection category]
  (let [{:keys [id name color tasks]} category
        sel-id                 (:id-or-nil current-selection)
        sel-cat                (:type-or-nil current-selection)
        is-selected            (and (= :category sel-cat)
                                    (= id sel-id))
        is-child-selected      (->> category
                                    (:tasks)
                                    (some #(if (= sel-id (:id %)) true nil))
                                    (some?))
        is-grandchild-selected (if is-child-selected
                                 (->> category
                                      (:tasks)
                                      (some #(if (= sel-id (:id %)) %))
                                      ((fn [task] ;; pulls periods into one seq
                                         (concat (:planned-periods task) (:actual-periods task))))
                                      (some #(if (= sel-id (:id %)) true nil))
                                      (some?)))
        ordered-tasks          (sort-by (fn [task]
                                          (+ (if (:complete task) 100 0)
                                             0                                                                          ;; TODO use first character alphabet value of name to provide a two level order
                                             ;; using just sort by and no java comparator
                                             ))
                                        tasks)
        children  (into [(r/as-element
                          [ui/raised-button {:key               (str "add-task-for-category-" id)
                                             :href              "#/add/task" :label "Add Task"
                                              :background-color "grey"
                                              :style            {:margin-top "1em"
                                                      :margin-left "2em"
                                                      :margin-bottom "1em"}}])]
                        (->> ordered-tasks
                             (map #(assoc % :color color))
                             (map (partial list-task current-selection))))]

    [ui/list-item {:key             id
                   :primaryText     (uic/concatenated-text name 20
                                                           "no name entered ...")
                   :leftIcon        (r/as-element (uic/svg-mui-circle color))
                   :nestedItems     children
                   :open            (or is-selected
                                        is-child-selected
                                        is-grandchild-selected)
                   :style           (if is-selected {:backgroundColor "#ededed"})
                   :onClick         (fn [e]
                                      (when-not is-selected
                                        (rf/dispatch [:set-selected-category id])))
                   :on-double-click (fn [e]
                                      (when is-selected
                                        (hist/nav! (str "/edit/category/" id))))}]))
