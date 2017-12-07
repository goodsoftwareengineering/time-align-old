(ns time-align.ui.app-bar
  (:require [re-frame.core :as rf]
            [time-align.ui.common :as uic]
            [oops.core :refer [oget oset!]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [time-align.js-interop :as jsi]))

(defn app-bar []
  (let [
        main-drawer-state @(rf/subscribe [:main-drawer-state])
        ]
    [:div.app-bar-container
     {:style {:display    "flex"
              :flex       "1 0 100%"
              ;; :border "green solid 0.1em"
              :box-sizing "border-box"}}
     [ui/app-bar {:title                    "Time Align"
                  :onLeftIconButtonTouchTap (fn [e] (rf/dispatch [:toggle-main-drawer]))}]
     [ui/drawer {:docked             false
                 :open               main-drawer-state
                 :disableSwipeToOpen true
                 :onRequestChange    (fn [new-state] (rf/dispatch [:set-main-drawer new-state]))}

      [:a {:href "/#"}
       [ui/menu-item {:onTouchTap    #(do
                                        (rf/dispatch [:set-main-drawer false])
                                        (rf/dispatch [:set-active-page {:page-id :home}]))
                      :innerDivStyle {:display "flex" :align-items "center"}}
        (uic/svg-mui-time-align {:color "black"
                             :style {:marginRight "0.5em"}})
        [:span "Home"]]]

      [:a {:href "#/list"}
       [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}}
        (uic/svg-mui-entity {:type :all :color "black" :style {:marginRight "0.5em"}})
        [:span "List"]]]

      [:a {:href "#/agenda"}
       [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}}
        [ic/action-view-agenda {:style {:marginRight "0.5em"}}]
        [:span "Agenda"]]]

      [:a {:href "#/queue"}
       [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}}
        [ic/action-toc {:style {:marginRight "0.5em"}}]
        [:span "Queue"]]]

      [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}
                     :disabled      true}
       [ic/action-settings {:style {:marginRight "0.5em"}}]
       [:span "Settings"]]

      [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}
                     :disabled      true}
       [ic/social-person {:style {:marginRight "0.5em"
                                  :color       "grey"}}]
       [:span "Account"]]

      [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}
                     :on-click      (fn []
                                      (let [input (-> (jsi/get-elements-by-class-name "import-file-input")
                                                      (aget 0))]
                                        (jsi/click! input)))}
       [:input.import-file-input {:type      "file"
                                  :style     {:display "none"}
                                  :on-change (fn [e]
                                               (let [inputter (-> (jsi/get-elements-by-class-name "import-file-input")
                                                                  (aget 0)
                                                                  (oget "files")
                                                                  (aget 0))]
                                                 (rf/dispatch [:import-app-db inputter])))}]
       [ic/file-cloud-upload {:style {:marginRight "0.5em"}}]
       [:span "Import"]]

      [ui/menu-item {:innerDivStyle {:display "flex" :align-items "center"}
                     :on-click      time-align.storage/export-app-db}
       [ic/content-save {:style {:marginRight "0.5em"}}]
       [:span "Export"]]


      ]]
    )
  )
