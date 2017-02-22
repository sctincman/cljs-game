(ns cljs-game.input
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]))

(enable-console-print!)

(defrecord ^:export InputComponent [keymap state])

;;need to handle keyup, via previous issue with making keyboard more versatile
(defn- translate-input
  "Given a keymap, filters and transforms the raw Javascript event"
  [keymap event]
  (let [key (.-key event)]
    (keymap key)))

;;placeholder, right state behavior here to figure out how to structure the rest
;; How to get current state?
;; on-exit (call before calling next state function)
;; can emit (:turning-right-to-left, etc)
;; possibly new signal? add a duration to the state?
;;;; or, just have time as a second input signal,
(defn movement-states [command-signal]
  (let [out-signal (s/signal nil "input-movement-fsm")]
    (letfn [(enter-moving-right []
              ;;on-enter behavior
              (println "Walking right")
              (s/propagate out-signal :moving-right)
              (s/watch command-signal
                     (:tag out-signal)
                     (fn [tag old new]
                       (condp = new
                         :left (enter-moving-left)
                         :down (enter-standing)
                         nil))))
            (enter-moving-left []
              (println "Walking left")
              (s/propagate out-signal :moving-left)
              (s/watch command-signal
                     (:tag out-signal)
                     (fn [tag old new]
                       (condp = new
                         :right (enter-moving-right)
                         :down (enter-standing)
                         nil))))
            (enter-standing []
              ;;on-enter behavior
              (println "Standing")
              (s/propagate out-signal :standing)
              (s/watch command-signal
                     (:tag out-signal)
                     (fn [tag old new]
                       (condp = new
                         :left (enter-moving-left)
                         :right (enter-moving-right)
                         nil))))]
      (enter-standing)
      out-signal)))

(defn ^:export movement
  "Given a keymap and entity, add input-driven movement component to entity, and returns updated entity."
  [entity keymap]
  (let [input-signal (s/map (fn [event]
                              (println (.-key event))
                              (println (keymap (.-key event)))
                              (keymap (.-key event)))
                          (s/keyboard))]
    ;; check if exists?
    (assoc-in entity [:components :movement-component]
              (->InputComponent keymap (movement-states input-signal)))))

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
     "q" {:type :input :action :forward :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :velocity :z] - 0.5))}
     "e" {:type :input :action :backward :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :velocity :z] + 0.5))}
     "t" {:type :input :action :up :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :y] + 0.01))}
     "f" {:type :input :action :left :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :x] - 0.01))}
     "g" {:type :input :action :down :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :y] - 0.01))}
     "h" {:type :input :action :right :target :player
          :execute (fn [entity] (update-in entity [:components :body-component :acceleration :x] + 0.01))}}))

(defn ^:export handle-input! [event]
  (let [key (.-key event)
        command (@input-mapping key)]
    (when-not (.-repeat event))
    (when command
      (if (= :player (:target command))
        (swap! input-queue conj (@input-mapping key))
        ((:execute command))))))

(defn controllable? [entity]
  (and (:input-component (:components entity))
       (:command-component (:components entity))))

(defn commandable? [entity]
  (some? (:command-component (:components entity))))

(defn pull-from-input! [command-stream attempts]
  (let [command (peek @input-queue)]
    (if (and (pos? attempts)
             (some? command))
      (do (swap! input-queue pop)
        (pull-from-input!
          (conj command-stream command)
          (dec attempts)))
      command-stream)))

(defn ^:export process-input [entities]
  (let [commands (pull-from-input! nil 10)]
    (map (fn [entity]
           (if (controllable? entity)
             (update-in entity [:components :command-component :commands] concat commands)
             entity))
         entities)))

(defn perform-commands [entity]
  (-> (reduce (fn [entity command]
                (if (:execute command)
                  ((:execute command) entity)
                  entity))
              entity
              (get-in entity [:components :command-component :commands]))
      (assoc-in [:components :command-component :commands] nil)))

(defn ^:export process-commands [entities]
  (map (fn [entity]
         (if (commandable? entity)
           (perform-commands entity)
           entity))
       entities))
