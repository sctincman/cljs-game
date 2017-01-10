(ns cljs-game.core
  (:require [threejs :as three]
            [cljs-game.render :as render]
            [cljs-game.input :as input]))

(enable-console-print!)

(def world (atom {:accum-time 0.0
                  :player-stream []
                  :running true
                  :entities []}))

(defn update-world
  [delta-time]
  (let [player (reduce (fn [entity command]
                        (cond
                          (= :left (command :action)) (update-in entity [:components :position-component :x] - 10)
                          (= :right (command :action)) (update-in entity [:components :position-component :x] + 10)
                          (= :up (command :action)) (update-in entity [:components :position-component :y] - 10)
                          (= :down (command :action)) (update-in entity [:components :position-component :y] + 10)))
                         (first (:entities @world)) (@world :player-stream))]
    (swap! world assoc :entities [player])
    (swap! world assoc :player-stream [])))

(defn game-loop
  [now]
  (let [prev (@world :prev-time)
        leftover-time (@world :accum-time)
        time-step 16.666666666666668]
    (swap! world assoc :prev-time now)
    (let [player-stream (input/process-input nil 10)]
      (swap! world assoc :player-stream (vec (concat (@world :player-stream)
                                                     player-stream))))
    (when (@world :running)
      (swap! world assoc :leftover-time
             (loop [accumulated (+ leftover-time (- now prev))
                    attempts 10]
               (if (and (>= accumulated time-step)
                        (>= attempts 0))
                 (do (update-world time-step)
                     (recur (- accumulated time-step) (dec attempts)))
                 accumulated))))))

(defn ^:export init-game
  []
  (let [scene (three/Scene.)
        camera (three/PerspectiveCamera. 75 (/ js/window.innerWidth js/window.innerHeight) 0.1, 1000)
        renderer (three/WebGLRenderer.)
        light (three/AmbientLight. 0x404040)
        light2 (three/PointLight. 0xffffff 2 0)
        test-cube (render/test-cube scene)]
    (set! (.-z (.-position camera)) 900) 
    (.add scene light)
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (js/document.addEventListener "keydown" input/handle-input)
    (swap! world assoc :prev-time js/Performance.now)
    (swap! world assoc :entities [test-cube])
    (swap! input/input-mapping assoc "i" {:type :input
                                          :action :info
                                          :target :none
                                          :execute (fn [] (println @world))})
    (swap! input/input-mapping assoc "p" {:type :input
                          :action :pause
                          :target :world
                          :execute (fn [] (swap! world assoc :running
                                                (not (@world :running))))})
    (let [animate (fn animate [current-time]
                    (js/requestAnimationFrame animate)
                    (game-loop current-time)
                    (render/render renderer scene camera (:entities @world)))]
      (animate js/Performance.now))))

