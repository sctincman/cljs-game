(defproject cljs-game "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [org.clojure/clojurescript "1.9.293"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-cljsbuild "1.1.5"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {
              :builds {
                       :main {
                              :source-paths ["src/cljs/"]
                              :compiler {
                                         :output-to "resources/public/js/main.js"
                                         :foreign-libs [{:file "resources/libs/three.module.js"
                                                         :provides  ["threejs"]
                                                         :module-type :es6}]
                                         :optimizations :whitespace
                                         :pretty-print true
                                        ;:source-map "resources/public/js/main.js.map"
                                         :language-in :ecmascript5
                                         :language-out :ecmascript5}}
                       :prod {
                              :source-paths ["src/cljs/"]
                              :jar true
                              :compiler {
                                         :output-to "resources/public/js/main.min.js"
                                         :foreign-libs [{:file "resources/libs/three.module.js"
                                                         :provides  ["threejs"]
                                                         :module-type :es6}]
                                         :optimizations :advanced
                                         :pretty-print false
                                        ;:source-map "resources/public/js/main.js.map"
                                         :language-in :ecmascript5
                                         :language-out :ecmascript5}}}}
  :ring {:handler cljs-game.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
