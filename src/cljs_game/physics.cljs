(ns cljs-game.physics
  (:require [cljs-game.entity :as ecs]))

(defrecord ^:export BodyComponent [velocity acceleration])

(defn physical? [entity]
  (and (:body-component (:components entity))
       (:position-component (:components entity))))

(defn propagate [body delta-t]
  (-> body
      (update-in [:components :position-component :x] + (* delta-t (get-in body [:components :body-component :velocity :x])))
      (update-in [:components :position-component :y] + (* delta-t (get-in body [:components :body-component :velocity :y])))
      (update-in [:components :position-component :z] + (* delta-t (get-in body [:components :body-component :velocity :z])))
      (update-in [:components :body-component :velocity :x] + (* delta-t (get-in body [:components :body-component :acceleration :x])))
      (update-in [:components :body-component :velocity :y] + (* delta-t (get-in body [:components :body-component :acceleration :y])))
      (update-in [:components :body-component :velocity :z] + (* delta-t (get-in body [:components :body-component :acceleration :z])))))

(defn ^:export update-bodies
  [entities delta-t]
  (map (fn [entity]
         (if (physical? entity)
           (propagate entity delta-t)
           entity))
       entities))
