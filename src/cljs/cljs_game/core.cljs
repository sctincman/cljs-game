(ns cljs-game.core
  (:require [threejs :as three]))

(enable-console-print!)

(def world (atom {}))

(def command-queue (atom cljs.core/PersistentQueue.EMPTY))

(defn handle-input [event]
  (let [key (.-key event)]
    (swap! command-queue conj
           (case key
             "w" {:type :input :key :up}
             "a" {:type :input :key :left}
             "s" {:type :input :key :down}
             "d" {:type :input :key :right}
             "p" {:type :input :key :pause}))
    (when (not (.-repeat event))
      (print (.-repeat event)))))

(defn process-input []
  (loop [attempts 10]
    (let [command (peek @command-queue)]
      (if command
        ;; change? What if somethign else pops between above and below
        ;; have function check if peek and item are same item, yay! then do pop
        ;; else, recur w/o doing anything to try again.
        (do (swap! command-queue pop)
            (print command)
            (when (and (= (command :type) :input)
                       (= (command :key) :pause))
              (swap! world assoc :running
                     (not (@world :running))))
            (recur (dec attempts)))))))

(defn update-world
  [delta-time]
  (let [mesh (@world :mesh)]
    (set! (.-x (.-rotation mesh)) (+ 0.01 (.-x (.-rotation mesh))))
    (set! (.-y (.-rotation mesh)) (+ 0.02 (.-y (.-rotation mesh))))
    (swap! world assoc :mesh mesh)))

(defn game-loop
  [now]
  (let [prev (@world :prev-time)
        leftover-time (@world :accum-time)
        time-step 16.666666666666668]
    (swap! world assoc :prev-time now)
    (process-input)
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
    (swap! world assoc :prev js/Performance.now)
    (swap! world assoc :accum-time 0.0)
    (let [animate (fn animate [current-time]
                    (js/requestAnimationFrame animate)
                    (game-loop current-time)
                    (.render renderer scene camera))]
      (animate js/Performance.now))))

