(ns ^:figwheel-no-load time-align.app
  (:require [time-align.core :as core]
            [devtools.core :as devtools]
            [figwheel.client :as figwheel :include-macros true]
            [re-frisk.core :refer [enable-re-frisk!]]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :on-jsload core/mount-components)

(devtools/install!)

(core/init!)
