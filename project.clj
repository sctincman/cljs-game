(defproject cljs-game "0.1.0-SNAPSHOT"
  :description "CLJS based WebGL game engine"
  :url "http://github.com/sctincman/cljs-game"
  :license "MIT"
  :min-lein-version "2.0.0"
  :source-paths ["src/"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.391"
                  :exclusions [org.clojure/tools.reader]]
                 [org.clojure/data.json "0.2.6"] ;for CLJ, not CLJS
                 [cljs-ajax "0.5.8"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-codox "0.10.2"]
            [lein-figwheel "0.5.8"]
            [lein-cljsbuild "1.1.5" :exclusions [[org.clojure/clojure]]]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds {:dev {:source-paths ["src"]
                             :figwheel {:on-jsload "cljs-game.core/on-js-reload"}
                             :compiler {:main cljs-game.core
                                        :output-dir "resources/public/js"
                                        :output-to "resources/public/js/main.js"
                                        :asset-path "js"
                                        :source-map-timestamp true
                                        :foreign-libs [{:file "resources/libs/three.module.js"
                                                        :provides  ["threejs"]
                                                        :module-type :es6}]
                                        :externs ["resources/libs/externs.js"]
                                        :optimizations :none
                                        :pretty-print true
                                        :language-in :ecmascript5
                                        :language-out :ecmascript5
                                        :preloads [devtools.preload]}}
                       :main {:source-paths ["src"]
                              :jar true
                              :compiler {:main cljs-game.core
                                         :output-to "resources/public/js/main.min.js"
                                         :output-dir "target/build-main"
                                         :foreign-libs [{:file "resources/libs/three.module.js"
                                                         :provides  ["threejs"]
                                                         :module-type :es6}]
                                         :externs ["resources/libs/externs.js"]
                                         :optimizations :simple ;grr threejs not compatible with anything higher...
                                         :pretty-print false
                                         :language-in :ecmascript5
                                         :language-out :ecmascript5}}}}
  :clean-targets ^{:protect false} ["resources/public/js/build-dev" "target"]
  :figwheel {:css-dirs ["resources/public/css"]}
  :codox {:language :clojurescript
          :output-path "resources/public/doc"
          :source-uri "https://github.com/sctincman/cljs-game/blob/{version}/{filepath}#L{line}"
          ;; Be explicit that undocumented public fns should be documented
          :metadata {:doc "TODO: Add docstring"
                     :doc/format :markdown}}
  :ring {:handler cljs-game.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]
                        [binaryage/devtools "0.8.2"]
                        [figwheel-sidecar "0.5.8"]
                        [com.cemerick/piggieback "0.2.1"]]
         :source-paths ["src" "dev"]
         :repl-options {; for nREPL dev you really need to limit output
                        :init (set! *print-length* 50)
                        :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
