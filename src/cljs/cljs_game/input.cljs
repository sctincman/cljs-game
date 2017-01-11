(ns cljs-game.input
  (:require [cljs-game.ecs :as ecs]))

(enable-console-print!)

(defrecord InputComponent [queue])

(defrecord CommandComponent [commands])

(def input-queue (atom cljs.core/PersistentQueue.EMPTY))

(def input-mapping (atom
                    { "w" {:type :input :action :up :target :player
                           :execute (fn [entity] (update-in entity [:components :position-component :y] - 10))}
                     "a" {:type :input :action :left :target :player
                          :execute (fn [entity] (update-in entity [:components :position-component :x] - 10))}
                     "s" {:type :input :action :down :target :player
                          :execute (fn [entity] (update-in entity [:components :position-component :y] + 10))}
                     "d" {:type :input :action :right :target :player
                          :execute (fn [entity] (update-in entity [:components :position-component :x] + 10))}}))

(defn ^:export handle-input [event]
  (let [key (.-key event)
        command (@input-mapping key)]
    (when (not (.-repeat event)))
    (when command
      (if (= :player (:target command))
        (swap! input-queue conj (@input-mapping key))
        ((:execute command))))))

(defn controllable? [entity]
  (and (:input-component (:components entity))
       (:command-component (:components entity))))

(defn commandable? [entity]
  (:command-component (:components entity)))

(defn pull-from-input [command-stream attempts]
  (let [command (peek @input-queue)]
    (if (and command (> attempts 0))
      (do (swap! input-queue pop)
          (pull-from-input
           (conj command-stream command)
           (dec attempts)))
      command-stream)))

(defn ^:export process-input [entities]
  (let [controllables (filter controllable? entities)
        rest (remove controllable? entities)
        commands (pull-from-input nil 10)]
    (concat rest
            (map (fn [entity] (update-in entity [:components :command-component :commands] concat commands))
                 controllables))))

(defn ^:export process-commands [entities]
  (let [commandables (filter commandable? entities)
        rest (remove commandable? entities)]
    (concat rest
            (map (fn [entity]
                   (-> (reduce (fn [entity command]
                                 (when (:execute command)
                                   ((:execute command) entity)))
                               entity
                               (:commands (:command-component (:components entity))))
                       (assoc-in [:components :command-component :commands] nil)))
                 commandables))))
