(ns cljs-game.entity
  (:require ))

(defrecord ^:export Entity [identifier triggers behaviors])

(defrecord ^:export PositionComponent [x y z])
