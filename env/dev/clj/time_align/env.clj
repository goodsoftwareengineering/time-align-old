(ns time-align.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [time-align.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[time-align started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[time-align has shut down successfully]=-"))
   :middleware wrap-dev})
