(ns cljs-game.render.threejs.camera
  (:require [cljs-game.render.core :as render]
            [cljs-game.vector :as v]
            [threejs :as three]))

(defrecord ^:export ThreeJSCamera [object]
  render/IRenderable
  (prepare [this entity]
    (when (some? (:position entity))
      (set! (.-x (.-position object))
            (get-in entity [:position :x]))
      (set! (.-y (.-position object))
            (get-in entity [:position :y]))
      (set! (.-z (.-position object))
            (get-in entity [:position :z])))))

(defn ^:export ThreeJSPerspectiveCamera [fov aspect near far]
  (let [camera (three/PerspectiveCamera. fov aspect near far)
        entity (-> {}
                   (assoc :position (v/vector 0 0 700))
                   (assoc :rotation nil)
                   (assoc :camera {:type :perspective}))]
    (update entity :renders conj
            (->ThreeJSCamera camera))))

(defn ^:export ThreeJSOrthoCamera [left right top bottom near far]
  (let [camera (three/OrthographicCamera. left right top bottom near far)
        entity (-> {}
                   (assoc :position (v/vector 0 0 700))
                   (assoc :rotation nil)
                   (assoc :camera {:type :orthographic}))]
    (update entity :renders conj
            (->ThreeJSCamera camera))))

