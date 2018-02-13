(ns time-align.ui.app-bar
  (:require [re-frame.core :as rf]
            [time-align.ui.common :as uic]
            [oops.core :refer [oget oset!]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [time-align.js-interop :as jsi]))

(def app-bar-options
  [{:href "/#"
    :label [:span "Home"]
    :icon (uic/svg-mui-time-align {:color "black"
                                   :style {:marginRight "0.5em"}})
    :on-touch-tap #(do
                     (rf/dispatch [:set-main-drawer false])
                     (rf/dispatch
                      [:set-active-page {:page-id :home}]))}

   {:href "#/list/categories"
    :label [:span "List"]
    :icon (uic/svg-mui-entity
           {:type :all :color "black"
            :style {:marginRight "0.5em"}})}

   {:href "#/calendar"
    :icon [ic/action-date-range {:style {:marginRight "0.5em"}}]
    :label [:span "Calendar"]}

   {:href "#/agenda"
    :label [:span "Agenda"]
    :icon [ic/action-view-agenda {:style {:marginRight "0.5em"}}]}

   {:href "#/queue"
    :label [:span "Queue"]
    :icon [ic/action-toc {:style {:marginRight "0.5em"}}]}

   {:label [:span "Settings"]
    :icon [ic/action-settings {:style {:marginRight "0.5em"}}]}

   {:label [:span "Account"]
    :icon [ic/social-person {:style {:marginRight "0.5em"}}]}

   {:label [:span "Import"]
    :icon [ic/file-cloud-upload {:style {:marginRight "0.5em"}}]
    :child [:input.import-file-input
            {:type      "file"
             :style     {:display "none"}
             :on-change
             (fn [e]
               (let [inputter
                     (-> (jsi/get-elements-by-class-name
                          "import-file-input")
                         (aget 0)
                         (oget "files")
                         (aget 0))]
                 (rf/dispatch [:import-app-db inputter])))}]
    :on-touch-tap (fn []
                    (let [input
                          (-> (jsi/get-elements-by-class-name
                               "import-file-input")
                              (aget 0))]
                      (jsi/click! input)))}

   {:on-touch-tap time-align.storage/export-app-db
    :icon [ic/content-save {:style {:marginRight "0.5em"}}]
    :label [:span "Export"]}

   ])

(defn app-bar-option [{:keys [href label icon on-touch-tap child]}]
  [:a (merge (when (some? href)
               {:href href})
             {:key label
              :style {:text-decoration "none"}})
   [ui/menu-item (merge
                  {:innerDivStyle {:display "flex" :align-items "center"}}
                  (when (some? on-touch-tap)
                    {:onTouchTap on-touch-tap} ))
    child icon label]])

(defn app-bar []
  (let [main-drawer-state @(rf/subscribe [:main-drawer-state])
        displayed-day       @(rf/subscribe [:displayed-day])
        ]
    [:div.app-bar-container
     {:style {:display    "flex"
              :flex       "1 0 100%"
              ;; :border "green solid 0.1em"
              :box-sizing "border-box"}}
     [ui/app-bar {:title       (jsi/->date-string displayed-day)
                  :onLeftIconButtonTouchTap (fn [e] (rf/dispatch [:toggle-main-drawer]))}]

     [ui/drawer {:docked             false
                 :open               main-drawer-state
                 :disableSwipeToOpen true
                 :onRequestChange    (fn [new-state] (rf/dispatch [:set-main-drawer new-state]))}

      (->> app-bar-options
           (map app-bar-option))]]))
