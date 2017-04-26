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

(def graze-animation
  {:duration 200
   :frames [{:x 0, :y 3}
            {:x 1, :y 3}
            {:x 2, :y 3}
            {:x 3, :y 3}
            {:x 2, :y 3}
            {:x 2, :y 3}
            {:x 3, :y 3}
            {:x 2, :y 3}
            {:x 2, :y 3}
            {:x 3, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}]})

(def graze-animation2
  {:duration 200
   :frames [{:x 0, :y 3}
            {:x 1, :y 3}
            {:x 2, :y 3}
            {:x 3, :y 3}
            {:x 2, :y 3}
            {:x 2, :y 3}
            {:x 3, :y 3}
            {:x 2, :y 3}
            {:x 2, :y 3}
            {:x 3, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 4}
            {:x 3, :y 4}
            {:x 2, :y 4}
            {:x 1, :y 4}
            {:x 0, :y 4}
            {:x 0, :y 4}
            {:x 0, :y 4}]})

(def run-animation
  {:duration 180
   :frames [{:x 0, :y 2}
            {:x 1, :y 2}
            {:x 2, :y 2}
            {:x 3, :y 2}
            {:x 4, :y 2}]})

(def stand-animation
  {:duration 200
   :frames [{:x 0, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 3}
            {:x 4, :y 4}
            {:x 3, :y 4}
            {:x 2, :y 4}
            {:x 1, :y 4}
            {:x 0, :y 4}
            {:x 0, :y 4}
            {:x 0, :y 4}]})


(defn ^:export start-game! [backend resources]
  (let [resources (update resources :deer render/sprite-sheet 64.0 64.0)
        test-sprite (-> {}
                        (assoc :position (v/vector 0 -200 0))
                        (update :renders conj (render/scale
                                               (render/key-texture
                                                (render/create-sprite backend (:deer resources))
                                                {:x 2, :y 2})
                                               2.0))
                        (input/movement {"a" :left, "d" :right, "s" :down})
                        (render/add-animation :stand stand-animation)
                        (render/add-animation :run run-animation)
                        (physics/body 1.0 0.5)
                        (collision/add-aabb v/zero 64.0 64.0 10.0)
                        (assoc :collisions (signals/signal nil "collision")))
        test-atlas2 (-> {}
                        (assoc :position (v/vector 0 -200 200))
                        (update :renders conj (render/scale
                                               (render/key-texture
                                                (render/create-sprite backend (:deer resources))
                                                {:x 2, :y 3})
                                               2.0))
                        (render/add-animation :graze graze-animation)
                        (render/set-animation :graze))
        test-atlas3 (-> {}
                        (assoc :position (v/vector 100 -200 300))
                        (update :renders conj (render/scale
                                               (render/key-texture
                                                (render/create-sprite backend (:deer resources))
                                                {:x 3, :y 2})
                                               2.0))
                        (render/add-animation :run run-animation)
                        (render/set-animation :run)
                        (ai/add-patrol (v/vector -300 0 300) (v/vector 300 0 300))
                        (physics/body 1.0 0.18)
                        (collision/add-aabb v/zero 64.0 64.0 10.0))
        a-deer (-> {}
                   (assoc :position (v/vector -400 -200 0))
                   (update :renders conj (render/scale
                                          (render/key-texture
                                           (render/create-sprite backend (:deer resources))
                                           {:x 3, :y 3})
                                          2.0))
                   (render/add-animation :graze graze-animation2)
                   (render/set-animation :graze)
                   (collision/add-aabb v/zero 64.0 64.0 1.0)
                   (assoc :collisions (signals/signal nil "collision")))
        space (-> {} (collision/add-space 1000.0))
        forest-0 (-> {}
                     (assoc :position (v/vector 0 0 -150))
                     (update :renders conj (render/scale
                                            (render/create-sprite
                                             backend
                                             (render/magnification-filter
                                              (:forest-0 resources)
                                              :nearest))
                                            2.0)))
        forest-1 (-> {}
                     (assoc :position (v/vector 0 0 -60))
                     (update :renders conj (render/scale
                                            (render/create-sprite
                                             backend
                                             (render/magnification-filter
                                              (:forest-1 resources)
                                              :nearest))
                                            2.0)))
        forest-2 (-> {}
                     (assoc :position (v/vector 0 0 -30))
                     (update :renders conj (render/scale
                                            (render/create-sprite
                                             backend
                                             (render/magnification-filter
                                              (:forest-2 resources)
                                              :nearest))
                                            2.0)))
        forest-3 (-> {}
                     (assoc :position (v/vector 0 0 0))
                     (update :renders conj (render/scale
                                            (render/create-sprite
                                             backend
                                             (render/magnification-filter
                                              (:forest-3 resources)
                                              :nearest))
                                            2.0)))
        ortho-camera (-> (render/ThreeJSOrthoCamera (/ js/window.innerWidth -2)
                                                    (/ js/window.innerWidth 2)
                                                    (/ js/window.innerHeight 2)
                                                    (/ js/window.innerHeight -2) 0.1 1000)
                         (input/movement {"q" :left, "e" :right})
                         (physics/body 1.0 0.5))
        pers-camera (-> (render/ThreeJSPerspectiveCamera 75 (/ js/window.innerWidth js/window.innerHeight) 0.1 1000)
                        (ai/follow :player (v/vector 0 100 700)))
        entities {:player
                  (render/state-animate test-sprite
                                        {:standing :stand
                                         :moving-left :run
                                         :moving-right :run}
                                        (:movement test-sprite))
                  :deer2 test-atlas2
                  :deer3 test-atlas3
                  :a-deer a-deer
                  :space space
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
    (signals/propagate (:bah test-atlas3) {:key :left, :press :down})
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

(defn -main [bah]
  (println bah))
