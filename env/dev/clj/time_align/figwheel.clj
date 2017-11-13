(ns time-align.figwheel
  (:require [figwheel-sidecar.repl-api :as ra]))

(defn start-fw []
  (ra/start-figwheel!)
  (ra/start-autobuild :worker))

(defn stop-fw []
  (ra/stop-figwheel!))

(defn cljs []
  (when-not (ra/figwheel-running?)
    (start-fw))
  (ra/cljs-repl "app"))

