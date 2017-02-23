(ns cljs-game.physics
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]))

(defrecord ^:export BodyComponent [velocity acceleration])

;;hmm body will need all forces, not just movement, hack for just movement now
(defn ^:export body [entity mass speed]
  (let [movement-state (get-in entity [:components :movement-component :state]) ;hmmm, may need to change this re fields/components
        velocity-signal (s/foldp (fn [velocity movement]
                                   (condp = (:state movement)
                                     :moving-right {:x speed, :y 0.0, :z 0.0}
                                     :moving-left {:x (- speed), :y 0.0, :z 0.0}
                                     :standing {:x 0.0, :y 0.0, :z 0.0}
                                     nil velocity))
                                 {:x 0.0, :y 0.0, :z 0.0}
                                 movement-state)
        acceleration-signal (s/signal {:x 0.0, :y 0.0, :z 0.0} "accel")]
    (assoc-in entity [:components :body-component]
              (->BodyComponent velocity-signal acceleration-signal))))


;;have body listen to a forces signal
;;foldp over body/time to propagate?
(defn physical? [entity]
  (and (:body-component (:components entity))
       (:position-component (:components entity))))

;;conv func, likely need to optimize
(defn vadd [u v]
  {:x (+ (:x u) (:x v))
   :y (+ (:y u) (:y v))
   :z (+ (:z u) (:z v))})

(defn vscale [a v]
  {:x (* a (:x v))
   :y (* a (:y v))
   :z (* a (:y v))})

(defn accelerate [body delta-t]
  (s/propagate (get-in body [:components :body-component :velocity])
               (vadd (s/value (get-in body [:components :body-component :velocity]))
                     (vscale delta-t
                             (s/value (get-in body [:components :body-component :acceleration])))))
  body)

(defn propagate [body delta-t]
  (-> body
      (update-in [:components :position-component]
                 vadd
                 (vscale delta-t (s/value (get-in body [:components :body-component :velocity]))))

      (accelerate delta-t)))

(defn ^:export update-bodies
  [entities delta-t]
  (map (fn [entity]
         (if (physical? entity)
           (propagate entity delta-t)
           entity))
       entities))
