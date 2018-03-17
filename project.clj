(defproject time-align "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [
                 [alandipert/storage-atom "2.0.1"]
                 [binaryage/oops "0.5.6"]
                 [buddy "2.0.0"]
                 [camel-snake-kebab "0.4.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [clj-oauth "1.5.4"]
                 [clj-time "0.14.2"]
                 [cljs-ajax "0.7.3"]
                 [cljsjs/filesaverjs "1.3.3-0"]
                 [cljs-react-material-ui "0.2.43"]
                 [cljsjs/js-joda "1.5.0-0"]
                 [cljsjs/moment-timezone "0.5.11-1"]
                 [clojure.java-time "0.3.0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.rpl/specter "1.0.5"]
                 [com.walmartlabs/lacinia "0.21.0"]
                 [compojure "1.6.0"]
                 [conman "0.7.5"]
                 [cprop "0.1.11"]
                 [day8.re-frame/async-flow-fx "0.0.6"]
                 [day8.re-frame/http-fx "0.1.2"]
                 [expound "0.3.4"]
                 [funcool/struct "1.2.0"]
                 [luminus-aleph "0.1.5"]
                 [luminus-migrations "0.4.5"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.2"]
                 [markdown-clj "1.0.2"]
                 [metosin/compojure-api "1.1.11"]
                 [metosin/muuntaja "0.5.0"]
                 [metosin/ring-http-response "0.9.0"]
                 [mount "0.1.11"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.reader "1.1.2"]
                 [org.postgresql/postgresql "42.2.0"]
                 [org.webjars.bower/tether "1.4.3"]
                 [org.webjars/bootstrap "4.0.0"]
                 [org.webjars/font-awesome "5.0.2"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"] ;; ported from old luminus version kept it because i dont' know what it is
                 [re-frame "0.10.2"]
                 [re-learn "0.1.1"]
                 [reagent "0.7.0" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [reagent-utils "0.2.1"]
                 [reanimated "0.5.1"] ;; TODO: don't know if we need this
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [secretary "1.2.3"]
                 [selmer "1.11.5"]
                 [stylefy "1.2.0"]
                 [org.clojure/test.check "0.9.0"] ;; TODO: move to dev deps when we don't need generation of data
               ]

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj" "test/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot time-align.core
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  :plugins [[cider/cider-nrepl "0.16.0-SNAPSHOT"]
            [migratus-lein "0.5.4"]
            [lein-cljsbuild "1.1.5"]]
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :worker :output-dir]
   [:cljsbuild :builds :app :worker :output-to]
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :server-ip   "0.0.0.0"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware
   [cemerick.piggieback/wrap-cljs-repl
    cider.nrepl/cider-middleware]}
  :profiles
  { 
   :uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min" "min-worker"]]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-dir "target/cljsbuild/public/js"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :source-map "target/cljsbuild/public/js/app.js.map"
                 :optimizations :whitespace
                 :pretty-print false
                 :pseudo-names    true
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :closure-defines {goog.DEBUG false}
                 :externs ["react/externs/react.js"]}}
               :min-worker
               {:source-paths ["src_worker/cljs"]
                :compiler     {:main          time-align.worker
                               :output-dir    "target/cljsbuild/public/js/out_worker"
                               :output-to     "target/cljsbuild/public/js/worker.js"
                               :optimizations :whitespace
                               :pretty-print  false}}}}

             :aot :all
             :uberjar-name "time-align.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-server" "-Dconf=dev-config.edn"]
                  :dependencies [[binaryage/devtools "0.9.9"]
                                 [com.cemerick/piggieback "0.2.2"]
                                 [org.clojure/tools.nrepl "0.2.10"]
                                 [doo "0.1.8"]
                                 [figwheel-sidecar "0.5.14"]
                                 [pjstadig/humane-test-output "0.8.3"]
                                 [prone "1.2.0"]
                                 [re-frisk "0.5.3"]
                                 [ring/ring-devel "1.6.3"]
                                 [ring/ring-mock "0.3.2"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.19.0"]
                                 [lein-doo "0.1.8"]
                                 [lein-figwheel "0.5.14"]
                                 [org.clojure/clojurescript "1.9.946"]]
                  :cljsbuild
                  {:builds
                   {:app
                    {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                     :figwheel {:websocket-url "ws://[[client-hostname]]:[[server-port]]/figwheel-ws"
                                :on-jsload "time-align.core/mount-components"}
                     :compiler
                     {:main "time-align.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true
                      :preloads [re-frisk.preload]}}
                    :worker
                    {:source-paths ["src_worker/cljs"]
                     :figwheel     {:websocket-url "ws://[[server-ip]]:[[server-port]]/figwheel-ws"}
                     :compiler     {:output-to     "target/cljsbuild/public/js/worker.js"
                                    :output-dir    "target/cljsbuild/public/js/out_worker"
                                    :source-map    true
                                    :optimizations :none}}}}

                  :doo {:build "test"}
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-server" "-Dconf=test-config.edn"]
                  :resource-paths ["env/test/resources"]
                  :cljsbuild
                  {:builds
                   {:test
                    {:source-paths ["src/cljc" "src/cljs" "src_worker/cljs" "test/cljc" "test/cljs"]
                     :compiler
                     {:output-to "target/test.js"
                      :main "time-align.doo-runner"
                      :optimizations :whitespace
                      :pretty-print true}}}}}
   :profiles/dev {}
   :profiles/test {}})
