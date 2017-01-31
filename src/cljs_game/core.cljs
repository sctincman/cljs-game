(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.entity :as ecs]
            [cljs-game.physics :as physics]
            [cljs-game.scene :as scene]
            [cljs-game.signals :as signals]))

(enable-console-print!)

(defonce ^:export world
  (atom {:accum-time 0.0
         :prev-time 0.0
         :running true
         :perspective true
         :entities []}))

(defn update-world
  [entities delta-time]
  entities)

(defn game-loop!
  [now]
  (let [prev (:prev-time @world)
        leftover-time (:accum-time @world)
        time-step 16.0]
    (swap! world assoc :prev-time now)
    (loop [accumulated (+ leftover-time (- now prev))
           attempts 10
           entities (:entities @world)]
      (if (and (>= accumulated time-step)
               (pos? attempts)
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

(defn ^:export js-start-game! []
  (let [backend (render/create-threejs-backend!)
        test-sprite (-> (ecs/->Entity 42 {} {})
                      (assoc-in [:components :position-component] (ecs/->PositionComponent 0 0 0))
                      (assoc-in [:components :render-component] (render/create-sprite-component! "assets/images/placeholder.png"))
                      (assoc-in [:components :command-component] (input/->CommandComponent nil))
                      (assoc-in [:components :input-component] (input/->InputComponent nil))
                      (assoc-in [:components :body-component] (physics/->BodyComponent {:x 0 :y 0 :z 0} {:x 0 :y 0 :z 0})))
        test-cube (-> (ecs/->Entity 43 {} {})
                      (assoc-in [:components :position-component] (ecs/->PositionComponent -400 100 20))
                      (assoc-in [:components :render-component] (render/create-cube-component)))
        background (-> (ecs/->Entity 0 {} {})
                       (assoc-in [:components :position-component] (ecs/->PositionComponent 0 0 -20))
                       (assoc-in [:components :render-component] (render/create-sprite-component! "assets/images/test-background.png")))]
    (render/add-to-backend backend test-sprite)
    (render/add-to-backend backend test-cube)
    (render/add-to-backend backend background)
    (js/document.addEventListener "keydown" input/handle-input!)
    (signals/watch (signals/foldp
                    (fn [acc x] (inc acc))
                    0
                    (signals/keyboard))
                   :count-keys
                   (fn [k o n] (println k " from signals: " o "->" n)))
    (signals/watch (signals/map
                    (fn [event] (.-key event))
                    (signals/keyboard))
                   :extract-key
                   (fn [k o n] (println k ": " o "->" n)))
    (signals/watch (signals/tick 1000)
                   :timed
                   (fn [k o n] (println k ": " o "->" n)))
    (signals/watch (signals/frames)
                   :frames
                   (fn [k o n] (println k ": " o "->" n)))
    (swap! world assoc :prev-time js/Performance.now)
    (swap! world assoc :entities [test-cube test-sprite])
    (swap! input/input-mapping assoc "i" {:type :input
                                          :action :info
                                          :target :none
                                          :execute (fn [] (println @world))})
    (swap! input/input-mapping assoc "p" {:type :input
                                          :action :pause
                                          :target :world
                                          :execute (fn [] (swap! world assoc :running
                                                                 (not (@world :running))))})
    (swap! input/input-mapping assoc "o" {:type :input
                                          :action :perspective
                                          :target :renderer
                                          :execute (fn [] (swap! world assoc :perspective
                                                                 (not (@world :perspective))))})
    (let [animate (fn animate [current-time]
                    (js/requestAnimationFrame animate)
                    (game-loop! current-time)
                    (render/render backend (:entities @world) (:perspective @world)))]
      (animate js/Performance.now))))

(defn on-js-reload []
  (println "Figwheel: reloaded!"))
