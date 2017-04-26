(ns cljs-game.scene
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]))

(defprotocol ^:export Scene
  "Protocol for management of entities involved in a scene."
  (spawn-entity [this identifier] "Create an entity from identifier")
  (load-entities! [this uri] "Load and cache entity prototypes from external source."))
