(ns cljs-game.handler
  (:require [cljs-game.views :as views]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defroutes app-routes
  (GET "/" {{is-debug "debug"} :query-params} (views/home-page))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
