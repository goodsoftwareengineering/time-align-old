(ns time-align.core
  (:require [reagent.core :as r]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [re-frame.core :as rf]
            [re-learn.core :as re-learn]
            [re-learn.views :as re-learn-views]
            [reanimated.core :as anim]
            [secretary.core :as secretary]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [time-align.ajax :refer [load-interceptors!]]
            [time-align.handlers]
            [time-align.ui.app-bar :as ab :refer [app-bar]]
            [time-align.ui.home :as hp]
            [time-align.ui.common :as uic]
            [time-align.ui.svg-day-view :as day-view]
            [time-align.history :as hist]
            [time-align.subscriptions]
            [clojure.string :as string]
            [time-align.client-utilities :as cutils]
            [time-align.tutorials :as tuts]
            [goog.string.format]
            [time-align.utilities :as utils]
            [oops.core :refer [oget oset!]]
            [cljs.pprint :refer [pprint]]
            [time-align.ui.agenda :as ap]
            [time-align.ui.entity-forms :as ef]
            [time-align.ui.list :as lp]
            [time-align.ui.queue :as qp]
            [time-align.ui.action-buttons :as actb]
            [time-align.ui.calendar :as cp]
            [time-align.js-interop :as jsi]))

;;Forward declarations to make file linting easier

(defn x-svg [{:keys [cx cy r fill stroke shadow click]}]
  (let [pi        (Math/PI)
        cx-int    (js/parseInt cx)
        cy-int    (js/parseInt cy)
        r-int     (js/parseInt r)
        r-int-adj (* 0.70 r-int)

        x1 (+ cx-int (* r-int-adj (Math/cos  (* pi (/ 3 4)))))
        y1 (+ cy-int (* r-int-adj (Math/sin  (* pi (/ 3 4)))))
        x2 (+ cx-int (* r-int-adj (Math/cos  (* pi (/ 7 4)))))
        y2 (+ cy-int (* r-int-adj (Math/sin  (* pi (/ 7 4)))))

        x3 (+ cx-int (* r-int-adj (Math/cos  (* pi (/ 1 4)))))
        y3 (+ cy-int (* r-int-adj (Math/sin  (* pi (/ 1 4)))))
        x4 (+ cx-int (* r-int-adj (Math/cos  (* pi (/ 5 4)))))
        y4 (+ cy-int (* r-int-adj (Math/sin  (* pi (/ 5 4)))))

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
                   {:d (str "M " x3 " " y3 " " "L " x4 " " y4 " ")})]]))

(defn +-svg [{:keys [cx cy r fill stroke shadow click]}]
  (let [pi        (Math/PI)
        cx-int    (js/parseInt cx)
        cy-int    (js/parseInt cy)
        r-int     (js/parseInt r)
        r-int-adj (* 0.70 r-int)

        x1 (+ cx-int (* r-int-adj (Math/cos  (* pi (/ 1 2)))))
        y1 (+ cy-int (* r-int-adj (Math/sin  (* pi (/ 1 2)))))
        x2 (+ cx-int (* r-int-adj (Math/cos  (* pi (/ 3 2)))))
        y2 (+ cy-int (* r-int-adj (Math/sin  (* pi (/ 3 2)))))

        x3 (+ cx-int (* r-int-adj (Math/cos  pi)))
        y3 (+ cy-int (* r-int-adj (Math/sin  pi)))
        x4 (+ cx-int (* r-int-adj (Math/cos  (* pi 2))))
        y4 (+ cy-int (* r-int-adj (Math/sin  (* pi 2))))

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
                   {:d (str "M " x3 " " y3 " " "L " x4 " " y4 " ")})]]))

(defn --svg [{:keys [cx cy r fill stroke shadow click]}]
  (let [pi        (Math/PI)
        cx-int    (js/parseInt cx)
        cy-int    (js/parseInt cy)
        r-int     (js/parseInt r)
        r-int-adj (* 0.70 r-int)

        x3 (+ cx-int (* r-int-adj (Math/cos  pi)))
        y3 (+ cy-int (* r-int-adj (Math/sin  pi)))
        x4 (+ cx-int (* r-int-adj (Math/cos  (* pi 2))))
        y4 (+ cy-int (* r-int-adj (Math/sin  (* pi 2))))

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

(defn svg-mui-stretch []
  [ui/svg-icon
   {:viewBox "0 0 24 24"}
   [:g
    [:polyline {:points "7,2 2,12 7,22"}]
    [:polyline {:points "11,2 13,2 13,22 11,22"}]
    [:polyline {:points "17,2 22,12 17,22"}]]])

(defn svg-mui-shrink []
  [ui/svg-icon
   {:viewBox "0 0 24 24"}
   [:g
    [:polyline {:points "2,2 7,12 2,22"}]
    [:polyline {:points "11,2 13,2 13,22 11,22"}]
    [:polyline {:points "22,2 17,12 22,22"}]]])

(defn current-quadrant []
  :q1)

(defn entity-forms [page]
  (let [page-value (if-let [entity-type (:type-or-nil page)]
                     entity-type
                     :category)
        entity-id  (:id-or-nil page)
        div-name   (keyword (str "div." page-value "-form"))]
    [:div.entity-form-container
     (app-bar)
     [ui/paper {:style {:padding "0.5em"}}
      (ef/entity-form page-value entity-id)]]))

(defn list-categories-page []
  (let [categories        @(rf/subscribe [:categories])
        selected          @(rf/subscribe [:selected])
        current-selection (:current-selection selected)]

    [:div {:style {:padding-bottom "10em"}}
     (app-bar)
     [ui/paper {:style {:width "100%"}}

      (lp/breadcrumbs [{:label (uic/svg-mui-entity
                                {:type :category
                                 :color (:text-color uic/app-theme)})
                        :link "#/list/categories"}])
      [ui/divider]

      [ui/list
       (->> categories
            (sort-by :name)
            (map (partial lp/list-item-category current-selection)))]]
     [:div.action-container ;; TODO this is used in two spots need to refactor to comp
      {:style {:position   "fixed"
               :right      "0"
               :bottom     "0"
               :z-index    "99"
               :padding    "0.75em"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      (actb/action-buttons-add-edit
       (fn [_] (hist/nav! (str "#/add/category" ))) ;; TODO use query params to fill in category
       current-selection
       :category)]]))

(defn list-tasks-page [id]
  (let [categories        @(rf/subscribe [:categories])
        tasks             (->> {:categories categories}
                               (cutils/pull-tasks )
                               (filter #(= (:category-id %) id)))
        parent-category   (some #(if (= (:id %) id) %) categories)
        selected          @(rf/subscribe [:selected])
        current-selection (:current-selection selected)]

    (if (nil? parent-category)
      [:div (app-bar) [ui/paper {:style {:width "100%"}}
                       [ui/subheader "No Category by that name :("]]]
      [:div {:style {:padding-bottom "10em"}}
       (app-bar)
       [ui/paper {:style {:width "100%"}}

        (lp/breadcrumbs [{:label (uic/svg-mui-entity
                                  {:type :task
                                   :color (:text-color uic/app-theme)})
                          :link "#/list/categories"
                          :color (:color parent-category)}
                         {:label (:name parent-category)
                          :link (str "#/list/tasks/" (:id parent-category))}])
        [ui/divider]

        [ui/list
         (->> tasks
              (sort-by :name)
              (map (partial lp/list-item-task current-selection)))]]

       [:div.action-container ;; TODO this is used in two spots need to refactor to comp
        {:style {:position   "fixed"
                 :right      "0"
                 :bottom     "0"
                 :z-index    "99"
                 :padding    "0.75em"
                 ;; :border "green solid 0.1em"
                 :box-sizing "border-box"}}
        (actb/action-buttons-add-edit
         (fn [_] (hist/nav! (str "#/add/task" ))) ;; TODO use query params to fill in category
         current-selection
         :task)]])))

(defn list-periods-page [id]
  (let [categories        @(rf/subscribe [:categories])
        periods             (->> {:categories categories}
                               (cutils/pull-periods)
                               (filter #(= (:task-id %) id)))
        tasks             (->> {:categories categories}
                               (cutils/pull-tasks )
                               (filter #(= (:id %) id)))
        parent-task       (first tasks)
        category-id       (:category-id parent-task)
        parent-category   (some #(if (= (:id %) category-id) %) categories)
        selected          @(rf/subscribe [:selected])
        current-selection (:current-selection selected)]

    (if (nil? parent-task)
      [:div (app-bar) [ui/paper {:style {:width "100%"}}
                       [ui/subheader "No Task by that name :("]]]
      [:div {:style {:padding-bottom "10em"}} ;; ensures actb don't block menu icon
       (app-bar)
       [ui/paper {:style {:width "100%"}}

        (lp/breadcrumbs [{:label (uic/svg-mui-entity
                                  {:type :period
                                   :color (:text-color uic/app-theme)})
                          :link "#/list/categories"
                          :color (:color parent-category)}
                         {:label (:name parent-category)
                          :link (str "#/list/tasks/" (:id parent-category))}
                         {:label (:name parent-task)
                          :link (str "#/list/periods/" (:id parent-task))}])
        [ui/divider]

        [ui/list
         (->> periods
              (sort-by (fn [period]
                         (if (cutils/period-has-stamps period)
                           (.valueOf (:stop period))
                           0)))
              (reverse)
              (map (partial lp/list-item-period current-selection)))]]

       [:div.action-container ;; TODO this is used in two spots need to refactor to comp
        {:style {:position   "fixed"
                 :right      "0"
                 :bottom     "0"
                 :z-index    "99"
                 :padding    "0.75em"
                 ;; :border "green solid 0.1em"
                 :box-sizing "border-box"}}
        (actb/action-buttons-add-edit
         (fn [_] (hist/nav! (str "#/add/period" ))) ;; TODO use query params to fill in category
         current-selection
         :period)]])))

(defn agenda-page []
  (let [selected @(rf/subscribe [:selected])
        periods  @(rf/subscribe [:periods])]
    [:div
     (app-bar)
     [ui/paper {:style {:width "100%"}}
      (ap/agenda selected periods)]

     [:div.action-container ;; TODO this is used in two spots need to refactor to comp
      {:style {:position   "fixed"
               :right      "0"
               :bottom     "0"
               :z-index    "99"
               :padding    "0.75em"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      (actb/action-buttons-add-edit
       (fn [_] (hist/nav! (str "#/add/category" ))) ;; TODO use query params to fill in category
       (:current-selection selected)
       :period)]]))

(defn queue-page []
  (let [tasks    @(rf/subscribe [:tasks])
        selected @(rf/subscribe [:selected])]
    [:div
     (app-bar)
     [ui/paper {:style {:width "100%"}}
      (qp/queue tasks selected)]
     [:div.action-container ;; TODO this is used in two spots need to refactor to comp
      {:style {:position   "fixed"
               :right      "0"
               :bottom     "0"
               :z-index    "99"
               :padding    "0.75em"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      (actb/action-buttons-add-edit
       (fn [_] (hist/nav! (str "#/add/category" ))) ;; TODO use query params to fill in category
       (:current-selection selected)
       :period)]
     ]))

(defn account-page []
  (let []

    [:div
     (app-bar)
     [ui/paper {:style {:width   "100%"
                        :padding "1em"}}
      [ui/text-field {:floating-label-text "Name"
                      :fullWidth           true}]
      [ui/text-field {:floating-label-text "Email"
                      :fullWidth           true}]]]))

(defn calendar-page []
  [:div ;; {:style {:width "100%"
        ;;          :height "100%"}}
   (app-bar)
   [ui/paper {:style {:width "100%"
                      ;; :height "100%"
                      }}
    (cp/calendar)]])

(def page
  (re-learn/with-tutorial
    {:id :intro-tutorial
     :name "Time Align"
     :description "Align planning and doing."
     :precedence 1
     :lessons [tuts/hello-lesson
               hp/home-page
               tuts/good-bye-lesson]}
    (fn []
      (let [this-page @(rf/subscribe [:page])
            page-id   (:page-id this-page)]
        [ui/mui-theme-provider
         {:mui-theme (get-mui-theme (aget js/MaterialUIStyles "DarkRawTheme"))}
         [ui/mui-theme-provider
          {:mui-theme (get-mui-theme uic/app-theme-with-component-overides)}
          [:div
           (case page-id
             :home              [hp/home-page]
             :add-entity-forms  (entity-forms this-page)
             :edit-entity-forms (entity-forms this-page)
             :list-categories   (list-categories-page)
             :list-tasks        (list-tasks-page (:id-or-nil this-page))
             :list-periods      (list-periods-page (:id-or-nil this-page))
             :account           (account-page)
             :agenda            (agenda-page)
             :queue             (queue-page)
             :calendar          (calendar-page)
             ;; default
             (hp/home-page))]]]))))

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute home-route "/" []
  (rf/dispatch [:set-active-page {:page-id :home}]))

(secretary/defroute agenda-route "/agenda" []
  (rf/dispatch [:set-main-drawer false])
  (rf/dispatch [:set-active-page {:page-id :agenda}])
  )

(secretary/defroute list-categories-route "/list/categories" []
  (rf/dispatch [:set-main-drawer false])
  (rf/dispatch [:set-selected-period nil])
  (rf/dispatch [:set-active-page {:page-id :list-categories}]))

(secretary/defroute list-tasks-route "/list/tasks/:category" [category]
  (rf/dispatch [:set-main-drawer false])
  (rf/dispatch [:set-selected-period nil])
  (rf/dispatch [:set-active-page {:page-id :list-tasks
                                  :type nil
                                  :id (uuid category)}]))

(secretary/defroute list-periods-route "/list/periods/:task" [task]
  (rf/dispatch [:set-main-drawer false])
  (rf/dispatch [:set-selected-period nil])
  (rf/dispatch [:set-active-page {:page-id :list-periods
                                  :type nil
                                  :id (uuid task)}]))

(secretary/defroute queue-route "/queue" []
  (rf/dispatch [:set-main-drawer false])
  (rf/dispatch [:set-active-page {:page-id :queue}]))

(secretary/defroute add-entity-route "/add/:entity-type" [entity-type query-params]
  ;; TODO this feels like it should be sync but gets error when it is
  ;; (rf/dispatch [:clear-entities])
  (rf/dispatch [:set-active-page {:page-id      :add-entity-forms
                                  :type         (keyword entity-type)
                                  :id           nil
                                  :query-params query-params}]))

(secretary/defroute edit-entity-route "/edit/:entity-type/:id" [entity-type id]
  (rf/dispatch [:set-active-page {:page-id :edit-entity-forms
                                  :type    (keyword entity-type)
                                  :id      (uuid id)}]))

(secretary/defroute calendar-route "/calendar" []
  (rf/dispatch [:set-main-drawer false])
  (rf/dispatch [:set-active-page {:page-id :calendar}]))

;; -------------------------
;; History
;; must be called after routes have been defined


;; -------------------------
;; Initialize app

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [page] (jsi/get-element-by-id "app"))
  (r/render [re-learn-views/tutorial {:context? true}]
            (jsi/get-element-by-id "tutorial")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (hist/hook-browser-navigation!)
  (re-learn/init)
  (mount-components)
  (js/setInterval uic/clock-tick 5000) ;; TODO this is bad
  )

