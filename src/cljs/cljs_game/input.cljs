(ns cljs-game.input
  (:require [cljs-game.ecs :as ecs]))

(def input-queue (atom cljs.core/PersistentQueue.EMPTY))

(def input-mapping (atom
                    { "w" {:type :input :action :up :target :player}
                     "a" {:type :input :action :left :target :player}
                     "s" {:type :input :action :down :target :player}
                     "d" {:type :input :action :right :target :player}}))

(defn ^:export handle-input [event]
  (let [key (.-key event)
        command (@input-mapping key)]
    (when (not (.-repeat event)))
    (when command
      (if (= :player (:target command))
        (swap! input-queue conj (@input-mapping key))
        ((:execute command))))))



(defn process-input [command-stream attempts]
  (let [command (peek @input-queue)]
    (if (and command (> attempts 0))
      (do (swap! input-queue pop)
          (process-input
           (conj command-stream command)
           (dec attempts)))
      command-stream)))

(defrecord InputComponent [queue])

(defrecord CommandComponent [commands])

