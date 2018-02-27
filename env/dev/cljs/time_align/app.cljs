(ns ^:figwheel-no-load time-align.app
  (:require [time-align.core :as core]
            [devtools.core :as devtools]))

(enable-console-print!)

(devtools/install!)

(core/init!)
