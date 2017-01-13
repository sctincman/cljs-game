(ns cljs-game.render
  (:require [cljs-game.entity :as ecs]
            [threejs :as three]))

(defrecord RenderComponent [mesh material geometry])

;; Hmm, we could use instance? but this works
(defn renderable? [entity]
  (and (:render-component (:components entity))
       (:position-component (:components entity))))

(defprotocol ^:export RenderBackend
  "Abstract the details of a backend into this standard interface."
  (render [this entities] "Using the list of entities, render and display the scene.")
  (add-to-backend [this entity] "Ensure backend is aware of an entity's render-components. Eg for Three.js this does Scene.add(mesh).")
  (prepare-scene [this entities] "Perform backend specific actions needed before rendering."))

(defrecord ^:export ThreeJSBackend [renderer scene camera objects]
  RenderBackend
  (add-to-backend [this entity]
    (.add scene (get-in entity [:components :render-component :mesh]))
    entity)
  (render [this entities]
    (let [entities (doall (prepare-scene this entities))]
      (.render renderer scene camera)
      entities))
  (prepare-scene [this entities]
    (let [renderables (filter renderable? entities)
          rest (remove renderable? entities)]
      (concat rest
              (map (fn [renderable]
                     (set! (.-x (.-position (:mesh (:render-component (:components renderable)))))
                           (:x (:position-component (:components renderable))))
                     (set! (.-y (.-position (:mesh (:render-component (:components renderable)))))
                           (:y (:position-component (:components renderable)))))
                   renderables)))))

(defn ^:export create-threejs-backend []
  (let [scene (three/Scene.)
        camera (three/PerspectiveCamera. 75 (/ js/window.innerWidth js/window.innerHeight) 0.1, 1000)
        renderer (three/WebGLRenderer.)
        light (three/AmbientLight. 0x404040)
        light2 (three/PointLight. 0xffffff 2 0)]
    (set! (.-z (.-position camera)) 900)
    (.add scene light)
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (->ThreeJSBackend renderer scene camera [light light2])))

(defn ^:export create-cube-component []
  (let [geometry (three/BoxGeometry. 200 200 200)
        material (three/MeshStandardMaterial. (js-obj "color" 0xff0040 "wireframe" false))
        mesh (three/Mesh. geometry material)
        render-component (->RenderComponent mesh material geometry)]
    render-component))
