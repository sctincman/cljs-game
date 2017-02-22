(ns cljs-game.core
  (:require [cljs-game.render :as render]
            [cljs-game.input :as input]
            [cljs-game.entity :as ecs]
            [cljs-game.physics :as physics]
            [cljs-game.scene :as scene]
            [cljs-game.signals :as signals]))

(enable-console-print!)

(defonce ^:export world
  {:running true
   :perspective true
   :entities []})

(defn step-entities
  [entities delta-time]
  (doall (-> entities
             ;; perhaps instead have it watch the step-signal?
             (physics/update-bodies delta-time))))

(defn step-world
  [state delta-time]
  (update state :entities #(step-entities % delta-time)))

(defn ^:export js-start-game! []
  (let [backend (render/create-threejs-backend!)
        test-sprite (-> (ecs/->Entity 42 {} {})
                      (assoc-in [:components :position-component] (ecs/->PositionComponent 0 0 0))
                      (assoc-in [:components :render-component] (render/create-sprite-component! "assets/images/placeholder.png"))
                      (input/movement {"a" :left, "d" :right, "s" :down})
                      (physics/body 1.0 0.5))
        test-cube (-> (ecs/->Entity 43 {} {})
                      (assoc-in [:components :position-component] (ecs/->PositionComponent -400 100 20))
                      (assoc-in [:components :render-component] (render/create-cube-component)))
        background (-> (ecs/->Entity 0 {} {})
                       (assoc-in [:components :position-component] (ecs/->PositionComponent 0 0 -20))
                       (assoc-in [:components :render-component] (render/create-sprite-component! "assets/images/test-background.png")))]
    (render/add-to-backend backend test-sprite)
    (render/add-to-backend backend test-cube)
    (render/add-to-backend backend background)
    (comment (signals/watch (signals/foldp
                             (fn [acc x] (inc acc))
                             0
                             (signals/keyboard))
                            :count-keys
                            (fn [k o n] (println k " from signals: " o "->" n))))
    (comment (signals/watch (signals/map
                             (fn [event] (.-key event))
                             (signals/keyboard))
                            :extract-key
                            (fn [k o n] (println k ": " o "->" n))))
    (comment (signals/watch (signals/tick 1000)
                            :timed
                            (fn [k o n] (println k ": " o "->" n))))
    (comment (signals/watch (signals/filter
                             (fn [k] (= k "s"))
                             (signals/map
                              (fn [event] (.-key event))
                              (signals/keyboard)))
                            :only-s
                            (fn [k o n] (println k ": " o "->" n))))
    (comment (signals/watch (signals/frames)
                            :frames
                            (fn [k o n] (println k ": " o "->" n))))

    (comment (swap! input/input-mapping assoc "i" {:type :input
                                                   :action :info
                                                   :target :none
                                                   :execute (fn [] (println @world))}))
    (comment (swap! input/input-mapping assoc "p" {:type :input
                                                      :action :pause
                                                      :target :world
                                                      :execute (fn [] (swap! world assoc :running
                                                                             (not (@world :running))))}))
    (comment (swap! input/input-mapping assoc "o" {:type :input
                                                   :action :perspective
                                                   :target :renderer
                                                   :execute (fn [] (swap! world assoc :perspective
                                                                          (not (@world :perspective))))}))
    ;;This... is our game loop!
    (signals/foldp (fn [state-signal step]
                     (render/render backend (:entities (signals/value state-signal)) true)
                     state-signal)
                   (signals/foldp step-world
                                  {:entities [test-cube test-sprite background]}
                                  (signals/delta-time (signals/tick 16.0)))
                   (signals/delta-time (signals/frames)))))

(defn on-js-reload []
  (println "Figwheel: reloaded!"))
