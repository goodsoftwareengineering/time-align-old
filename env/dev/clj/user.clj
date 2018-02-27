(ns user
  (:require [luminus-migrations.core :as migrations]
            [time-align.config :refer [env]]
            [mount.core :as mount]
            [time-align.figwheel :refer [start-fw stop-fw cljs]]
            [time-align.core :refer [start-app]]))

(defn start []
  (mount/start-without #'time-align.core/repl-server))

(defn stop []
  (mount/stop-except #'time-align.core/repl-server))

(defn restart []
  (stop)
  (start))

(defn migrate []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))


