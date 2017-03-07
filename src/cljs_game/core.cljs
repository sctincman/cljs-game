(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.entity :as ecs]
            [cljs-game.physics :as physics]
            [cljs-game.scene :as scene]
            [cljs-game.signals :as signals]
            [cljs-game.vector :as v]
            [cljs-game.ai :as ai]))

(enable-console-print!)

(defonce running-signal
  (signals/foldp (fn [run event]
                   (if (and (= "p" (:key event))
                            (= :down (:press event)))
                     (not run)
                     run))
                 true
                 input/keyboard))

(defn ^:export running? []
  (signals/value running-signal))

(defonce ortho-signal
  (signals/foldp (fn [ortho event]
                   (if (and (= "o" (:key event))
                            (= :down (:press event)))
                     (not ortho)
                     ortho))
                 true
                 input/keyboard))

(defn ^:export perspective? []
  (signals/value ortho-signal))

(defn step-entities
  [entities delta-time]
  (doall (-> entities
             (ai/propagate delta-time)
             (physics/update-bodies delta-time))))

(defn step-world
  [state delta-time]
  (if (running?)
    (step-entities state delta-time)
    state))

(defn ^:export js-start-game! []
  (let [backend (render/create-threejs-backend!)
        test-sprite (-> {}
                        (assoc :position v/zero)
                        (assoc :render (render/create-sprite-component! "assets/images/placeholder.png"))
                        (input/movement {"a" :left, "d" :right, "s" :down})
                        (physics/body 1.0 0.5))
        test-cube (-> {}
                      (assoc :position (v/vector -400 100 20))
                      (assoc :render (render/create-cube-component)))
        moving-cube (-> {}
                        (assoc :position (v/vector 400 -100 -20))
                        (assoc :render (render/create-cube-component))
                        (ai/add-patrol (v/vector -600 0 0) (v/vector 300 0 0))
                        (physics/body 1.0 0.2))
        background (-> {}
                       (assoc :position (v/vector 0 0 -20))
                       (assoc :render (render/create-sprite-component! "assets/images/test-background.png")))
        ortho-camera (-> (render/ThreeJSOrthoCamera (/ js/window.innerWidth -2)
                                                    (/ js/window.innerWidth 2)
                                                    (/ js/window.innerHeight 2)
                                                    (/ js/window.innerHeight -2) 0.1 1000)
                         (input/movement {"q" :left, "e" :right})
                         (physics/body 1.0 0.5))
        pers-camera (-> (render/ThreeJSPerspectiveCamera 75 (/ js/window.innerWidth js/window.innerHeight) 0.1 1000)
                        (ai/follow :player (v/vector 0 0 900)))
        entities {:player test-sprite
                  :a-cube test-cube
                  :m-cube moving-cube
                  :background background
                  :orthographic-camera ortho-camera
                  :perspective-camera pers-camera}
        ;;This... is our game loop!
        world (signals/foldp (fn [state-signal step]
                               (render/render backend (signals/value state-signal)
                                              (if (perspective?)
                                                :perspective-camera
                                                :orthographic-camera))
                               state-signal)
                             (signals/foldp step-world
                                            entities
                                            (signals/delta-time (signals/tick 16.0)))
                             (signals/delta-time (render/frames)))]
    (render/add-to-backend backend test-sprite)
    (render/add-to-backend backend test-cube)
    (render/add-to-backend backend moving-cube)
    (render/add-to-backend backend background)
    (signals/map (fn [event]
                   (when (and (= "i" (:key event))
                              (= :down (:press event)))
                       (println (signals/value world))))
                 input/keyboard)))

(defn on-js-reload []
  (println "Figwheel: reloaded!"))
