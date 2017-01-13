(ns cljs-game.input
  (:require [cljs-game.entity :as ecs]))

(enable-console-print!)

(defrecord InputComponent [queue])

(defrecord CommandComponent [commands])

(defonce input-queue (atom cljs.core/PersistentQueue.EMPTY))

(defonce input-mapping
  (atom
    {"w" {:type :input :action :up :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :velocity :y] + 0.5))}
     "a" {:type :input :action :left :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :velocity :x] - 0.5))}
     "s" {:type :input :action :down :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :velocity :y] - 0.5))}
     "d" {:type :input :action :right :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :velocity :x] + 0.5))}
     "t" {:type :input :action :up :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :y] + 0.01))}
     "f" {:type :input :action :left :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :x] - 0.01))}
     "g" {:type :input :action :down :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :y] - 0.01))}
     "h" {:type :input :action :right :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :x] + 0.01))}}))

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
