(defproject braid "0.0.1"
  :source-paths ["src"]

  :dependencies [;;server
                 [org.clojure/clojure "1.9.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [commons-codec "1.10"]
                 [commons-validator "1.5.1"]
                 [http-kit "2.4.0-beta1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2" :exclusions [ring/ring-core]]
                 [fogus/ring-edn "0.3.0"]
                 [ring-cors "0.1.12"]
                 [compojure "1.5.1"]
                 [environ "1.0.3"]
                 [com.taoensso/timbre "4.7.4" :exclusions [org.clojure/tools.reader com.taoensso/truss com.taoensso/encore]]
                 [crypto-password "0.2.0"]
                 [clj-time "0.12.0"]
                 [instaparse "1.4.2"]
                 [com.taoensso/carmine "2.13.1" :exclusions [com.taoensso/encore]]
                 [image-resizer "0.1.9"]
                 [clojurewerkz/quartzite "2.0.0"]
                 [inliner "0.1.0"]
                 [cljstache "2.0.1"]
                 [mount "0.1.10"]
                 [com.fasterxml.jackson.core/jackson-core "2.8.7"]
                 [com.cognitect/transit-clj "0.8.300" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [ring-transit "0.1.6" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [clout "2.1.2"]

                 ;;client
                 [org.clojure/clojurescript "1.10.238"]
                 [org.clojars.leanpixel/cljs-utils "0.4.2"]
                 [cljs-ajax "0.5.8"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.4"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [clj-fuzzy "0.3.2"]
                 [reagent  "0.8.1"]
                 [re-frame "0.10.5" :exclusions [org.clojure/clojurescript]]
                 [ring-middleware-format "0.7.4"]
                 [cljsjs/husl "6.0.1-0"]
                 [cljsjs/highlight "9.6.0-0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [hickory "0.7.0" :exclusions [org.clojure/clojurescript]]
                 [org.clojure/core.memoize "0.5.9"]
                 [lein-doo "0.1.7"]
                 [cljsjs/resize-observer-polyfill "1.4.2-0"]
                 [clucie "0.4.2"]

                 ;;shared
                 [org.clojure/tools.reader "1.3.0-alpha3"]
                 [org.clojure/core.async "0.4.474" :exclusions [org.clojure/tools.reader]]
                 [metosin/spec-tools "0.7.0"]
                 [org.clojure/test.check "0.9.0"] ; b/c spec-tools breaks without it
                 [com.taoensso/truss "1.3.6"]
                 [com.taoensso/sente "1.14.0-RC2" :exclusions [org.clojure/tools.reader taoensso.timbre com.taoensso/truss]]

                 ;;mobile
                 [garden "1.3.2"]]

  :main braid.core

  :plugins [[lein-environ "1.0.0"]
            [lein-cljsbuild "1.1.6" :exclusions [org.clojure/clojure]]
            [lein-doo "0.1.7"]]


  :clean-targets ^{:protect false}
  ["resources/public/js"]

  :figwheel {:server-port 3559}

  :cljsbuild {:test-commands {"once" ["lein" "doo" "phantom" "desktop-test" "once"]
                              "auto" ["lein" "doo" "phantom" "desktop-test" "auto"]}
              :builds
              [{:id "desktop-dev"
                :figwheel {:on-jsload "braid.core.client.desktop.core/reload"}
                :source-paths ["src/braid"
                               "src/retouch"]
                :compiler {:main braid.core.client.desktop.core
                           ;; uncomment to enable re-frame-10x (event debugger)
                           ;; :preloads [day8.re-frame-10x.preload]
                           :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                           :asset-path "/js/dev/desktop/"
                           :output-to "resources/public/js/dev/desktop.js"
                           :output-dir "resources/public/js/dev/desktop/"
                           :optimizations :none
                           :verbose false}}

               {:id "desktop-test"
                :source-paths ["src/braid"
                               "test/braid/test/client"]
                :compiler {:main braid.test.client.runners.doo
                           :optimizations :none
                           :output-to "resources/public/js/desktop/tests/out/all-tests.js"
                           :output-dir "resources/public/js/desktop/tests/out"}}

               {:id "mobile-dev"
                :figwheel {:on-jsload "braid.core.client.mobile.core/reload"}
                :source-paths ["src/braid"
                               "src/retouch"]
                :compiler {:main braid.core.client.mobile.core
                           :asset-path "/js/dev/mobile/"
                           :output-to "resources/public/js/dev/mobile.js"
                           :output-dir "resources/public/js/dev/mobile/"
                           :verbose true}}

               {:id "gateway-dev"
                :figwheel {:on-jsload "braid.core.client.gateway.core/reload"}
                :source-paths ["src/braid"]
                :compiler {:main braid.core.client.gateway.core
                           :asset-path "/js/dev/gateway/"
                           :output-to "resources/public/js/dev/gateway.js"
                           :output-dir "resources/public/js/dev/gateway"
                           :verbose true}}

               {:id "release"
                :source-paths ["src/braid"
                               "src/retouch"]
                :compiler {:asset-path "/js/prod/"
                           :output-dir "resources/public/js/prod/out"
                           :optimizations :advanced
                           :pretty-print false
                           :elide-asserts true
                           :closure-defines {goog.DEBUG false}
                           :modules {:cljs-base
                                     {:output-to "resources/public/js/prod/base.js"}
                                     :desktop
                                     {:output-to "resources/public/js/prod/desktop.js"
                                      :entries #{"braid.core.client.desktop.core"}}
                                     :gateway
                                     {:output-to "resources/public/js/prod/gateway.js"
                                      :entries #{"braid.core.client.gateway.core"}}
                                     :mobile
                                     {:output-to "resources/public/js/prod/mobile.js"
                                      :entries #{"braid.core.client.mobile.core"}}}
                           :verbose true}}]}

  :min-lein-version "2.5.0"

  :profiles {:datomic-free
             {:dependencies [[com.datomic/datomic-free "0.9.5697"
                              :exclusions [joda-time
                                           com.google.guava/guava
                                           org.slf4j/slf4j-api]]]}
             :datomic-pro
             {:dependencies [[com.datomic/datomic-pro "0.9.5201"
                              :exclusions [joda-time
                                           com.google.guava/guava]]
                             [org.postgresql/postgresql "9.3-1103-jdbc4"]]}

             :dev
             [:datomic-free
              {:source-paths ["src" "dev-src"]
               :global-vars {*assert* true}
               :repl-options {:timeout 120000
                              :init-ns braid.dev.core}
               :dependencies [[figwheel-sidecar "0.5.18"
                               :exclusions
                               [org.clojure/google-closure-library-third-party
                                com.google.javascript/closure-compiler]]
                              [com.bhauman/rebel-readline "0.1.2"]
                              [day8.re-frame/re-frame-10x "0.3.3"]]}]

             :prod
             [:datomic-free
              {:global-vars {*assert* false}}]

             :cider
             [:dev
              {:dependencies [[cider/piggieback "0.3.10"]]
               :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
               :plugins [[cider/cider-nrepl "0.20.0"]
                         [refactor-nrepl "2.4.0"]]}]

             :test
             [:dev]

             :uberjar
             [:prod
              {:aot [braid.core]
               :prep-tasks ["compile" ["cljsbuild" "once" "release"]]}]})
