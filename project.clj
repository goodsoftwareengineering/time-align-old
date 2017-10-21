(defproject time-align "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[alandipert/storage-atom "2.0.1"]
                 [buddy "1.3.0"]
                 [binaryage/oops "0.5.6"]
                 [cljs-ajax "0.5.9"]
                 [cljs-react-material-ui "0.2.43"]
                 [cljsjs/js-joda "1.5.0-0"]
                 [cljsjs/moment-timezone "0.5.11-0"]
                 [clojure.java-time "0.3.0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.rpl/specter "1.0.3"]
                 [compojure "1.5.2"]
                 [conman "0.6.3"]
                 [cprop "0.1.10"]
                 [day8.re-frame/async-flow-fx "0.0.6"]
                 [day8.re-frame/http-fx "0.1.2"]
                 [funcool/struct "1.0.0"]
                 [luminus-immutant "0.2.3"]
                 [luminus-migrations "0.4.2"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.2"]
                 [markdown-clj "0.9.98"]
                 [metosin/muuntaja "0.2.1"]
                 [metosin/ring-http-response "0.8.2"]
                 [mount "0.1.11"]
                 [org.clojure/clojure "1.9.0-alpha19"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.postgresql/postgresql "42.0.0"]
                 [org.webjars.bower/tether "1.4.0"]
                 [org.webjars/font-awesome "4.7.0"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [re-frame "0.9.2"]
                 [reagent "0.6.1" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [reagent-utils "0.2.1"]
                 [reanimated "0.5.1"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-core "1.6.0-RC3"]
                 [ring/ring-defaults "0.2.3"]
                 [secretary "1.2.3"]
                 [selmer "1.10.7"]]


  :min-lein-version "2.0.0"

  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj" "test/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot time-align.core
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  :plugins [[lein-cprop "1.0.1"]
            [migratus-lein "0.5.2"]
            [lein-cljsbuild "1.1.5"]
            [lein-immutant "2.1.0"]]
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :worker :output-dir]
   [:cljsbuild :builds :app :worker :output-to]
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :nrepl-port       7002
   :css-dirs         ["resources/public/css"]
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}


  :profiles
  {:uberjar       {:omit-source    true
                   :prep-tasks     ["compile" ["cljsbuild" "once" "min"]]
                   :cljsbuild
                                   {:builds
                                    {:min
                                     {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                                      :compiler
                                                    {:output-to     "target/cljsbuild/public/js/app.js"
                                                     :optimizations :advanced
                                                     :pretty-print  false
                                                     :closure-warnings
                                                                    {:externs-validation :off :non-standard-jsdoc :off}
                                                     :externs       ["react/externs/react.js"]}}}}


                   :aot            :all
                   :uberjar-name   "time-align.jar"
                   :source-paths   ["env/prod/clj"]
                   :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev   {:dependencies   [[binaryage/devtools "0.9.3"]
                                    [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                                    [doo "0.1.8"]
                                  [figwheel-sidecar "0.5.10"]
                                    [org.clojure/test.check "0.9.0"]
                                    [pjstadig/humane-test-output "0.8.1"]
                                    [prone "1.1.4"]
                                    [re-frisk "0.5.0"]
                                    [ring/ring-devel "1.5.1"]
                                    [ring/ring-mock "0.3.0"]]

                   :plugins        [[com.jakemccrary/lein-test-refresh "0.19.0"]
                                    [lein-doo "0.1.8"]
                                  [lein-figwheel "0.5.10"]
                                  [org.clojure/clojurescript "1.9.946"]]
                   :cljsbuild
                                   {:builds
                                    {:app
                                     {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                                      :compiler     {:main          "time-align.app"
                                                     :asset-path    "/js/out"
                                                     :output-to     "target/cljsbuild/public/js/app.js"
                                                     :output-dir    "target/cljsbuild/public/js/out"
                                                     :preloads      [re-frisk.preload]
                                                     :source-map    true
                                                     :optimizations :none
                                                     :pretty-print  true}}
                                     :worker
                                     {:source-paths ["src_worker/cljs"]
                                      :compiler {:output-to     "target/cljsbuild/public/js/worker.js"
                                                 :output-dir    "target/cljsbuild/public/js/out_worker"
                                                 :source-map    true
                                                 :optimizations :none}
                                      }}}
                   :doo            {:build "test"}
                   :source-paths   ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :repl-options   {:init-ns user}
                   :injections     [(require 'pjstadig.humane-test-output)
                                    (pjstadig.humane-test-output/activate!)]}
   :project/test  {:resource-paths ["env/test/resources"]
                   :cljsbuild
                                   {:builds
                                    {:test
                                     {:source-paths ["src/cljc" "src/cljs" "src_worker/cljs" "test/cljc" "test/cljs"]
                                      :compiler
                                                    {:output-to     "target/test.js"
                                                     :main          "time-align.doo-runner"
                                                     :optimizations :whitespace
                                                     :pretty-print  true}}}}}


   :profiles/dev  {}
   :profiles/test {}})
