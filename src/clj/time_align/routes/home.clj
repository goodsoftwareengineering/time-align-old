(ns time-align.routes.home
  (:require [time-align.layout :as layout]
            [time-align.utilities :as utils]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [ring.middleware.anti-forgery :refer :all]
            [time-align.db.core :as db]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
  (:import [java.net InetAddress]))

(defn home-page []
  (layout/render "home.html"))

(defroutes home-routes
           (GET "/" []
             (home-page))
           (GET "/docs" []
             (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                 (response/header "Content-Type" "text/plain; charset=utf-8")))
           (GET "/test-route" []
             (fn [req]
               (response/ok {:headers (:headers req)
                             :csrf-token *anti-forgery-token*}))
             )
           (POST "/analytics" []
             (fn [req]
               (->> req
                    :params
                    (merge {:ip_addr (InetAddress/getByName (:remote-addr req))})
                    utils/thread-friendly-pprint!
                    db/create-analytic!
                    (format "%d analytic(s) added")
                    response/ok))))
