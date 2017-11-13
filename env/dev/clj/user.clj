(ns user
  (:require [alembic.still :as still]
            [mount.core :as mount]
            [time-align.figwheel :refer [start-fw stop-fw cljs]]
            time-align.core))

(defn start []
  (mount/start-without #'time-align.core/repl-server))

(defn stop []
  (mount/stop-except #'time-align.core/repl-server))

(defn restart []
  (stop)
  (start))

(defn reload-project.clj
  (still/load-project))

