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

(defn get-ip-addr
  [req]
  (cond
    ;;When in dev use the local ip header, when deploy
    (-> time-align.config/env :dev) (InetAddress/getByName (:remote-addr req))
    ;;When in prod use the header from nginx
    ;;see https://stackoverflow.com/questions/34814957/nginx-does-not-forwards-remote-address-to-gunicorn#34816635
    (-> time-align.config/env :production) (->  req :headers (get "x-forwarded-for") InetAddress/getByName)))

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
                    (merge {:ip_addr (get-ip-addr req)})
                    db/create-analytic!
                    (format "%d analytic(s) added")
                    response/ok))))
