(ns cljs-game.core
  (:require [threejs :as three]))

(enable-console-print!)

(defn ^:export init-game
  [canvas-element]
  (let [context (.getContext canvas-element "2d")
        scene (three/Scene.)
        camera (three/PerspectiveCamera. 75 (/ js/window.innerWidth js/window.innerHeight) 0.1, 1000)
        renderer (three/WebGLRenderer.)
        geometry (three/BoxGeometry. 200 200 200)
        material (three/MeshBasicMaterial. (js-obj "color" 0xffe000 "wireframe" false))
        mesh (three/Mesh. geometry material)]
    (set! (.-z (.-position camera)) 900) 
    (.add scene mesh)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (set! (.-fillStyle context) "green")
    (.fillRect context 10 10 100 100)
    (let [animate (fn animate []
         (js/requestAnimationFrame animate)
         (set! (.-x (.-rotation mesh)) (+ 0.01 (.-x (.-rotation mesh))))
         (set! (.-y (.-rotation mesh)) (+ 0.02 (.-y (.-rotation mesh))))
                    (.render renderer scene camera))]
      (animate))))
