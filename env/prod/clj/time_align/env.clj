(ns time-align.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[time-align started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[time-align has shut down successfully]=-"))
   :middleware identity})
