(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.ecs :as ecs]))

(enable-console-print!)

(def world (atom {:accum-time 0.0
                  :running true
                  :entities []}))

(defn update-world
  [delta-time]
  )

(defn game-loop
  [now]
  (let [prev (@world :prev-time)
        leftover-time (@world :accum-time)
        time-step 16.666666666666668]
    (swap! world assoc :prev-time now)
    (let [entities (-> (:entities @world)
                       (input/process-input)
                       (input/process-commands))]
      (swap! world assoc :entities entities))
    (when (@world :running)
      (swap! world assoc :leftover-time
             (loop [accumulated (+ leftover-time (- now prev))
                    attempts 10]
               (if (and (>= accumulated time-step)
                        (>= attempts 0))
                 (do (update-world time-step)
                     (recur (- accumulated time-step) (dec attempts)))
                 accumulated))))))

(defn ^:export init-game []
  (let [backend (render/create-threejs-backend)
        test-cube (-> (ecs/->Entity 42 {})
                      (assoc-in [:components :position-component] (ecs/->PositionComponent 0 0))
                      (assoc-in [:components :render-component] (render/create-cube-component))
                      (assoc-in [:components :command-component] (input/->CommandComponent nil))
                      (assoc-in [:components :input-component] (input/->InputComponent nil)))]
    (render/add-to-backend backend test-cube)
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
                    (render/render backend (:entities @world)))]
      (animate js/Performance.now))))

