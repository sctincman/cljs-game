(ns cljs-game.entity
  (:require ))

(defprotocol ^:export Entity)

(defprotocol ^:export Component)

(defprotocol ^:export System)

(defrecord ^:export Entity [id components])

(defrecord ^:export Component [name])

(defrecord ^:export PositionComponent [x y z])
