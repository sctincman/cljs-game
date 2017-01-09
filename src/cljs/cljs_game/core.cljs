(ns cljs-game.core
  (:require [threejs :as three]))

(enable-console-print!)

(def world (atom {}))

(def input-queue (atom cljs.core/PersistentQueue.EMPTY))

(def input-mapping (atom
                    { "w" {:type :input :action :up :target :player}
                     "a" {:type :input :action :left :target :player}
                     "s" {:type :input :action :down :target :player}
                     "d" {:type :input :action :right :target :player}
                     "i" {:type :input
                          :action :info
                          :target :none
                          :execute (fn [] (println @world))}
                     "p" {:type :input
                          :action :pause
                          :target :world
                          :execute (fn [] (swap! world assoc :running
                                                (not (@world :running))))}}))

(defn handle-input [event]
  (let [key (.-key event)]
    (when (not (.-repeat event)))
    (swap! input-queue conj (@input-mapping key))))

(defn process-input [player-commands attempts]
  (let [command (peek @input-queue)]
    (if (and command (> attempts 0))
      (do (swap! input-queue pop)
          (process-input
           (let [result
                 (if (= :player (command :target))
                   (conj player-commands command)
                   (do ((command :execute))
                       nil))]
             (if result result player-commands))
           (dec attempts)))
      player-commands)))

(defn update-world
  [delta-time]
  (let [mesh (reduce (fn [mesh command]
                       (cond
                         (= :left (command :action)) (set! (.-x (.-rotation mesh)) (- (.-x (.-rotation mesh)) 0.01 ))
                         (= :right (command :action)) (set! (.-x (.-rotation mesh)) (+ 0.01 (.-x (.-rotation mesh))))
                         (= :up (command :action)) (set! (.-y (.-rotation mesh)) (- (.-y (.-rotation mesh)) 0.01))
                         (= :down (command :action)) (set! (.-y (.-rotation mesh)) (+ 0.01 (.-y (.-rotation mesh)))))
                       mesh)
                     (@world :mesh) (@world :player-stream))]
    (swap! world assoc :player-stream [])
    (swap! world assoc :mesh mesh)))

(defn game-loop
  [now]
  (let [prev (@world :prev-time)
        leftover-time (@world :accum-time)
        time-step 16.666666666666668]
    (swap! world assoc :prev-time now)
    (let [player-stream (process-input nil 10)]
      (swap! world assoc :player-stream (vec (concat (@world :player-stream)
                                                     player-stream))))
    (when (@world :running)
      (swap! world assoc :leftover-time
             (loop [accumulated (+ leftover-time (- now prev))]
               (if (>= accumulated time-step)
                 (do (update-world time-step)
                     (recur (- accumulated time-step)))
                 accumulated))))))

(defn ^:export init-game
  []
  (let [scene (three/Scene.)
        camera (three/PerspectiveCamera. 75 (/ js/window.innerWidth js/window.innerHeight) 0.1, 1000)
        renderer (three/WebGLRenderer.)
        geometry (three/BoxGeometry. 200 200 200)
        material (three/MeshStandardMaterial. (js-obj "color" 0xff0040 "wireframe" false))
        mesh (three/Mesh. geometry material)
        light (three/AmbientLight. 0x404040)
        light2 (three/PointLight. 0xffffff 2 0)]
    (set! (.-z (.-position camera)) 900) 
    (.add scene mesh)
    (swap! world assoc :mesh mesh)
    (.add scene light)
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (js/document.addEventListener "keydown" handle-input)
    (swap! world assoc :prev-time js/Performance.now)
    (swap! world assoc :accum-time 0.0)
    (swap! world assoc :player-stream [])
    (swap! world assoc :running true)
    (let [animate (fn animate [current-time]
                    (js/requestAnimationFrame animate)
                    (game-loop current-time)
                    (.render renderer scene camera))]
      (animate js/Performance.now))))

