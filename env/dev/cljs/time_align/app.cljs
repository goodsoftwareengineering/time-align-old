(ns ^:figwheel-no-load time-align.app
  (:require [time-align.core :as core]
            [devtools.core :as devtools]
            [re-frisk.core :refer [enable-re-frisk!]]))

(enable-console-print!)

(devtools/install!)

(core/init!)
