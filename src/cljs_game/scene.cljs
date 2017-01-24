(ns cljs-game.scene
  (:require [cljs-game.entity :as ecs]
            [ajax.core :as ajax]))

(defprotocol ^:export Scene
  "Protocol for management of entities involved in a scene."
  (spawn-entity [this identifier] "Create an entity from identifier")
  (load-entities! [this uri] "Load and cache entity prototypes from external source."))

(defrecord ^:export ThreeJSScene [scene entities cached-resources]
  Scene
  (load-entities!
    [this uri]
    (let [entity (ecs/->Entity 0 {})]
      (ajax/GET uri {:response-format :json
                     :handler (fn [response]
                                ;;pullout json
                                ;;(message :scene :entity-loaded :json (.-body response))
                                )}))))
