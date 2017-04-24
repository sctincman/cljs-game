(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.entity :as ecs]
            [cljs-game.physics :as physics]
            [cljs-game.scene :as scene]
            [cljs-game.signals :as signals]
            [cljs-game.vector :as v]
            [cljs-game.collision :as collision]
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
             (physics/update-bodies delta-time)
             (collision/handle-collisions))))

(defn step-world
  [state delta-time]
  (if (running?)
    (step-entities state delta-time)
    state))

(defn ^:export start-game! [backend resources]
  (let [resources (update resources :deer render/sprite-sheet 64.0 64.0)
        test-sprite (-> {}
                        (assoc :position v/zero)
                        (update :renders conj (render/create-sprite backend (:placeholder resources)))
                        (input/movement {"a" :left, "d" :right, "s" :down})
                        (physics/body 1.0 0.5)
                        (collision/add-aabb v/zero 174.0 564.0 1.0)
                        (assoc :collisions (signals/signal nil "collision")))
        test-atlas (-> {}
                        (assoc :position (v/vector 0 0 50))
                        (update :renders conj (render/create-sprite backend
                                                                    (render/subtexture (:deer resources)
                                                                                       128 64 64 64))))
        test-atlas2 (-> {}
                        (assoc :position (v/vector 0 0 200))
                        (update :renders conj (render/create-sprite backend (render/getsub (:deer resources)
                                                                                    {:x 0, :y 2}))))
        test-atlas3 (-> {}
                        (assoc :position (v/vector 0 0 500))
                        (update :renders conj (render/key-texture (render/create-sprite backend (:deer resources))
                                                                  {:x 0, :y 3})))
        test-cube (-> {}
                      (assoc :position (v/vector -400 100 0))
                      (update :renders conj (render/test-cube backend))
                      (collision/add-aabb v/zero 200.0 200.0 200.0)
                      (assoc :collisions (signals/signal nil "collision")))
        moving-cube (-> {}
                        (assoc :position (v/vector 400 -50 -20))
                        (update :renders conj (render/test-cube backend))
                        (ai/add-patrol (v/vector -600 0 0) (v/vector 300 0 0))
                        (physics/body 1.0 0.2)
                        (collision/add-aabb v/zero 200.0 200.0 200.0))
        background (-> {}
                       (assoc :position (v/vector 0 0 -200))
                       (update :renders conj (render/create-sprite backend (:background resources)))
                       (collision/add-space 1000.0))
        forest-0 (-> {}
                     (assoc :position (v/vector 0 0 -150))
                     (update :renders conj (render/scale (render/create-sprite backend (:forest-0 resources))
                                                         8.0)))
        forest-1 (-> {}
                     (assoc :position (v/vector 0 0 -60))
                     (update :renders conj (render/scale (render/create-sprite backend (:forest-1 resources))
                                                         8.0)))
        forest-2 (-> {}
                     (assoc :position (v/vector 0 0 -30))
                     (update :renders conj (render/scale (render/create-sprite backend (:forest-2 resources))
                                                         8.0)))
        forest-3 (-> {}
                     (assoc :position (v/vector 0 0 0))
                     (update :renders conj (render/scale (render/create-sprite backend (:forest-3 resources))
                                                         8.0)))
        ortho-camera (-> (render/ThreeJSOrthoCamera (/ js/window.innerWidth -2)
                                                    (/ js/window.innerWidth 2)
                                                    (/ js/window.innerHeight 2)
                                                    (/ js/window.innerHeight -2) 0.1 1000)
                         (input/movement {"q" :left, "e" :right})
                         (physics/body 1.0 0.5))
        pers-camera (-> (render/ThreeJSPerspectiveCamera 75 (/ js/window.innerWidth js/window.innerHeight) 0.1 1000)
                        (ai/follow :player (v/vector 0 0 700)))
        entities {:player test-sprite
                  :deer test-atlas
                  :deer2 test-atlas2
                  :deer3 test-atlas3
                  :a-cube test-cube
                  :m-cube moving-cube
                  :background background
                  :forest-0 forest-0
                  :forest-1 forest-1
                  :forest-2 forest-2
                  :forest-3 forest-3
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
    ;(signals/watch (:collisions test-sprite) :debug-collisions (fn [target old new] (println "Collision! " new)))
    ;(signals/watch (:collisions test-cube) :debug-collisions (fn [target old new] (println "Collision! " new)))
    (signals/map (fn [event]
                   (when (and (= "i" (:key event))
                              (= :down (:press event)))
                       (println (signals/value world))))
                 input/keyboard)
    world))

(defn on-js-reload []
  (println "Figwheel: reloaded!"))

(defn ^:export js-start-game! []
  (render/load-resources! start-game!))
