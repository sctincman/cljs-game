(ns cljs-game.render
  (:require [cljs-game.entity :as ecs]
            [threejs :as three]))

(defrecord RenderComponent [backend])

(defrecord ScaleComponent [x y z])

(defn renderable? [entity]
  (and (:render entity)
       (:position entity)))

(defprotocol ^:export RenderBackend
  "Abstract the details of a backend into this standard interface."
  (render [this entities perspective?] "Using the list of entities, render and display the scene.")
  (add-to-backend [this entity] "Ensure backend is aware of an entity's render-components. Eg for Three.js this does Scene.add(mesh).")
  (prepare-scene [this entities] "Perform backend specific actions needed before rendering."))

(defrecord ^:export ThreeJSBackend [renderer scene perspective-camera ortho-camera objects]
  RenderBackend
  (add-to-backend [this entity]
    (.add scene (get-in entity [:render :backend :mesh]))
    entity)
  (render [this entities perspective?]
    (let [entities (doall (prepare-scene this entities))]
      (if perspective?
        (.render renderer scene perspective-camera)
        (.render renderer scene ortho-camera))
      entities))
  (prepare-scene [this entities]
    (map (fn [entity]
           (if (renderable? entity)
             (do
               (set! (.-x (.-position (get-in entity [:render :backend :mesh])))
                     (get-in entity [:position :x]))
               (set! (.-y (.-position (get-in entity [:render :backend :mesh])))
                     (get-in entity [:position :y]))
               (set! (.-z (.-position (get-in entity [:render :backend :mesh])))
                     (get-in entity [:position :z])))
             entity))
         entities)))

(defn ^:export create-threejs-backend! []
  (let [scene (three/Scene.)
        camera (three/PerspectiveCamera. 75 (/ js/window.innerWidth js/window.innerHeight) 0.1 1000)
        ortho-camera (three/OrthographicCamera. (/ js/window.innerWidth -2)
                                                (/ js/window.innerWidth 2)
                                                (/ js/window.innerHeight 2)
                                                (/ js/window.innerHeight -2) 0.1 1000)
        renderer (three/WebGLRenderer.)
        light (three/AmbientLight. 0x404040)
        light2 (three/PointLight. 0xffffff 2 0)]
    (set! (.-z (.-position camera)) 900)
    (set! (.-z (.-position ortho-camera)) 900)
    (.add scene light)
    (set! (.-background scene) (three/Color. 0x6c6c6c))
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (->ThreeJSBackend renderer scene camera ortho-camera [light light2])))

(defn ^:export create-cube-component []
  (let [geometry (three/BoxGeometry. 200 200 200)
        material (three/MeshStandardMaterial. (js-obj "color" 0xff0040 "wireframe" false))
        mesh (three/Mesh. geometry material)]
    (->RenderComponent {:mesh mesh, :material material, :geometry geometry})))

;; Because Three.js loads the texture asynchronously, we cannot get the default scale at creation, and need to use this annoying workaround
;; (Yay closures!)
(defn ^:export create-sprite-component! [image-url]
  (let [material (three/SpriteMaterial.)
        sprite (three/Sprite. material)
        loader (three/TextureLoader.)]
    (.load loader image-url
           (fn [texture]
             (set! (.-map material) texture)
             (set! (.-x (.-scale sprite)) (.-width (.-image texture)))
             (set! (.-y (.-scale sprite)) (.-height (.-image texture)))
             (set! (.-needsUpdate material) true)))
    (->RenderComponent {:mesh sprite, :material material})))
