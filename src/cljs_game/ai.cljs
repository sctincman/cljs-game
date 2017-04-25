(ns cljs-game.ai
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]
            [cljs-game.input :as input]
            [cljs-game.vector :as v]))

(defn- patrol
  [left right signal]
  (fn [entity dt world]
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
                (assoc :movement (input/movement-fsm command-signal))
                (assoc :bah command-signal))]
    (s/propagate command-signal {:key :left, :press :down})
    bah))

(defn ^:export propagate
  "Propagate AI behavior over time"
  [entities delta-t]
  (reduce-kv (fn [entities id entity]
               (if (some? (:ai entity))
                 (assoc entities id ((:ai entity) entity delta-t entities))
                 entities))
             entities
             entities))

(defn- follow*
  [target offset]
  (fn [entity dt world]
    (let [followee (get world target)]
      (if (and (some? (:position entity))
               (some? (:position followee)))
        ;;complicated behavior goes here
        (assoc entity :position (v/add (:position followee)
                                       offset))
        entity))))

;; compose behaviors?

(defn ^:export follow
  [entity target offset]
  ;; add position if it isn' there
  ;; add in behavior component
  (assoc entity :ai (follow* target offset)))
