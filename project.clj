(defproject time-align "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[buddy "2.0.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [cider/cider-nrepl "0.15.1"]
                 [clj-oauth "1.5.4"]
                 [clj-time "0.14.2"]
                 [cljs-ajax "0.7.3"]
                 [com.walmartlabs/lacinia "0.21.0"]
                 [compojure "1.6.0"]
                 [conman "0.7.5"]
                 [cprop "0.1.11"]
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
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.reader "1.1.2"]
                 [org.postgresql/postgresql "42.2.0"]
                 [org.webjars.bower/tether "1.4.3"]
                 [org.webjars/bootstrap "4.0.0"]
                 [org.webjars/font-awesome "5.0.2"]
                 [re-frame "0.10.2"]
                 [reagent "0.7.0"]
                 [reagent-utils "0.2.1"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [secretary "1.2.3"]
                 [selmer "1.11.5"]]

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot time-align.core
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  :plugins [[migratus-lein "0.5.4"]
            [lein-cljsbuild "1.1.5"]]
  :clean-targets ^{:protect false}
  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware
   [cemerick.piggieback/wrap-cljs-repl cider.nrepl/cider-middleware]}
  

  :profiles
  { 
   :uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-dir "target/cljsbuild/public/js"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :source-map "target/cljsbuild/public/js/app.js.map"
                 :optimizations :advanced
                 :pretty-print false
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :externs ["react/externs/react.js"]}}}}
             
             
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
                     :figwheel {:on-jsload "time-align.core/mount-components"}
                     :compiler
                     {:main "time-align.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true
                      :preloads [re-frisk.preload]}}}}

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
                    {:source-paths ["src/cljc" "src/cljs" "test/cljs"]
                     :compiler
                     {:output-to "target/test.js"
                      :main "time-align.doo-runner"
                      :optimizations :whitespace
                      :pretty-print true}}}}
                  
                  }
   :profiles/dev {}
   :profiles/test {}})
