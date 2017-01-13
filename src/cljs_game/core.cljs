(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.entity :as ecs]
            [cljs-game.physics :as physics]))

(enable-console-print!)

(defonce ^:export world (atom {:accum-time 0.0
                  :prev-time 0.0
                  :running true
                  :entities []}))

(defn update-world
  [entities delta-time]
  entities)

(defn game-loop
  [now]
  (let [prev (@world :prev-time)
        leftover-time (@world :accum-time)
        time-step 16.0]
    (swap! world assoc :prev-time now)
    (loop [accumulated (+ leftover-time (- now prev))
           attempts 10
           entities (:entities @world)]
      (if (and (>= accumulated time-step)
               (>= attempts 0)
               (:running @world))
        (recur (- accumulated time-step)
               (dec attempts)
               (-> entities
                   (input/process-input)
                   (input/process-commands)
                   (physics/update-bodies time-step)
                   (update-world time-step)))
        (do (swap! world assoc :leftover-time accumulated)
          (swap! world assoc :entities entities))))))

(defn ^:export init-game []
  (let [backend (render/create-threejs-backend)
        test-cube (-> (ecs/->Entity 42 {})
                      (assoc-in [:components :position-component] (ecs/->PositionComponent 0 0))
                      (assoc-in [:components :render-component] (render/create-cube-component))
                      (assoc-in [:components :command-component] (input/->CommandComponent nil))
                      (assoc-in [:components :input-component] (input/->InputComponent nil))
                      (assoc-in [:components :body-component] (physics/->BodyComponent {:x 0 :y 0} {:x 0 :y 0})))]
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

(defn on-js-reload []
  (println "Figwheel: reloaded!"))
