(ns cljs-game.core
  (:require [threejs :as three]))

(enable-console-print!)

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
    (.add scene light)
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (let [animate (fn animate []
         (js/requestAnimationFrame animate)
         (set! (.-x (.-rotation mesh)) (+ 0.01 (.-x (.-rotation mesh))))
         (set! (.-y (.-rotation mesh)) (+ 0.02 (.-y (.-rotation mesh))))
                    (.render renderer scene camera))]
      (animate))))
