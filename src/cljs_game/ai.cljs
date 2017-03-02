(ns cljs-game.ai
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]
            [cljs-game.input :as input]))

(defn- patrol
  [left right signal]
  (fn [entity dt]
    (when (some? (:position entity))
      (let [pos (:position entity)]
        (when (< (:x pos) (:x left))
          (s/propagate signal {:key :right, :press :down}))
        (when (> (:x pos) (:x right))
          (s/propagate signal {:key :left, :press :down}))))
    entity))

(defn ^:export add-patrol
  "Simple AI. Cause them to walk back and forth"
  [entity left right]
  (let [command-signal (s/signal nil "patrol-ai")
        bah (-> entity
                (assoc :ai (patrol left right command-signal))
                (assoc :movement (input/movement-fsm command-signal)))]
    (s/propagate command-signal {:key :left, :press :down})
    bah))

(defn ^:export propagate
  "Propagate AI behavior over time"
  [entities delta-t]
  (map (fn [entity]
         (if (some? (:ai entity))
           ((:ai entity) entity delta-t)
           entity))
       entities))
