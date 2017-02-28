(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.entity :as ecs]
            [cljs-game.physics :as physics]
            [cljs-game.scene :as scene]
            [cljs-game.signals :as signals]))

(enable-console-print!)

(defonce running-signal
  (signals/foldp (fn [run event]
                   (if (and (= "p" (:key event))
                            (= :down (:press event)))
                     (not run)
                     run))
                 true
                 signals/keyboard))

(defn ^:export running? []
  (signals/value running-signal))

(defonce ortho-signal
  (signals/foldp (fn [ortho event]
                   (if (and (= "o" (:key event))
                            (= :down (:press event)))
                     (not ortho)
                     ortho))
                 true
                 signals/keyboard))

(defn ^:export perspective? []
  (signals/value ortho-signal))

(defn step-entities
  [entities delta-time]
  (doall (-> entities
             ;; perhaps instead have it watch the step-signal?
             (physics/update-bodies delta-time))))

(defn step-world
  [state delta-time]
  (if (running?)
    (update state :entities #(step-entities % delta-time))
    state))

(defn ^:export js-start-game! []
  (let [backend (render/create-threejs-backend!)
        test-sprite (-> (ecs/->Entity 42 {} {})
                        (assoc :position (ecs/->PositionComponent 0 0 0))
                        (assoc :render (render/create-sprite-component! "assets/images/placeholder.png"))
                        (input/movement {"a" :left, "d" :right, "s" :down})
                        (physics/body 1.0 0.5))
        test-cube (-> (ecs/->Entity 43 {} {})
                      (assoc :position (ecs/->PositionComponent -400 100 20))
                      (assoc :render (render/create-cube-component)))
        background (-> (ecs/->Entity 0 {} {})
                       (assoc :position (ecs/->PositionComponent 0 0 -20))
                       (assoc :render (render/create-sprite-component! "assets/images/test-background.png")))
        ;;This... is our game loop!
        world (signals/foldp (fn [state-signal step]
                               (render/render backend (:entities (signals/value state-signal)) (perspective?))
                               state-signal)
                             (signals/foldp step-world
                                            {:entities [test-cube test-sprite background]}
                                            (signals/delta-time (signals/tick 16.0)))
                             (signals/delta-time (signals/frames)))]
    (render/add-to-backend backend test-sprite)
    (render/add-to-backend backend test-cube)
    (render/add-to-backend backend background)
    (signals/map (fn [event]
                   (when (and (= "i" (:key event))
                              (= :down (:press event)))
                       (println (signals/value world))))
                 signals/keyboard)))

(defn on-js-reload []
  (println "Figwheel: reloaded!"))
