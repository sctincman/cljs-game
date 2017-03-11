(ns cljs-game.render
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]
            [cljs-game.vector :as v]
            [threejs :as three]))

(enable-console-print!)

(defn ^:export frames
  "Returns a signal that triggers when a new frame needs to be rendered, with the value of the absolute time. CLJS uses `requestAnimationFrame`."
  []
  (let [out-signal (s/signal (system-time) "frames")]
    (letfn [(callback [time]
              (s/propagate out-signal time)
              (.requestAnimationFrame js/window callback))]
      (callback (system-time))
      out-signal)))

(defrecord RenderComponent [backend])

(defrecord ScaleComponent [x y z])

(defn renderable? [entity]
  (and (:render entity)
       (:position entity)))

(defprotocol ^:export RenderBackend
  "Abstract the details of a backend into this standard interface."
  (render [this entities camera] "Using the list of entities, render and display the scene. Camera is the key of the camera entity to use for rendering.")
  (add-to-backend [this entity] "Ensure backend is aware of an entity's render-components. Eg for Three.js this does Scene.add(mesh).")
  (prepare-scene [this entities] "Perform backend specific actions needed before rendering."))

(defprotocol ^:export ICamera
  "Abstract camera"
  (look-at [this point] "Rotate camera to look at a point"))

(defrecord ^:export ThreeJSBackend [renderer scene objects]
  RenderBackend
  (add-to-backend [this entity]
    (.add scene (get-in entity [:render :backend :object]))
    entity)
  (render [this entities camera]
    (let [entities (doall (prepare-scene this entities))]
      (.render renderer scene (get-in entities [camera :render :backend :object]))
      entities))
  (prepare-scene [this entities]
    (reduce-kv (fn [entities id entity]
                 (when (renderable? entity)
                   (set! (.-x (.-position (get-in entity [:render :backend :object])))
                         (get-in entity [:position :x]))
                   (set! (.-y (.-position (get-in entity [:render :backend :object])))
                         (get-in entity [:position :y]))
                   (set! (.-z (.-position (get-in entity [:render :backend :object])))
                         (get-in entity [:position :z])))
                 entities)
               entities
               entities)))

(defrecord ^:export ThreeJSCamera [js-object position rotation])

(defn ^:export ThreeJSPerspectiveCamera [fov aspect near far]
  (let [camera (three/PerspectiveCamera. fov aspect near far)]
    (-> (ecs/->Entity (gensym "perspective-camera") {} {})
        (assoc :position (v/vector 0 0 900))
        (assoc :rotation nil)
        (assoc :render {:backend {:object camera}})
        (assoc :camera {:type :perspective}))))

(defn ^:export ThreeJSOrthoCamera [left right top bottom near far]
  (let [camera (three/OrthographicCamera. left right top bottom near far)]
    (-> (ecs/->Entity (gensym "ortho-camera") {} {})
        (assoc :position (v/vector 0 0 900))
        (assoc :rotation nil)
        (assoc :render {:backend {:object camera}})
        (assoc :camera {:type :orthographic}))))

(defn ^:export create-threejs-backend! []
  (let [scene (three/Scene.)
        renderer (three/WebGLRenderer.)
        light (three/AmbientLight. 0x404040)
        light2 (three/PointLight. 0xffffff 2 0)]
    (.add scene light)
    (set! (.-background scene) (three/Color. 0x6c6c6c))
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (->ThreeJSBackend renderer scene [light light2])))

(defn ^:export create-cube-component []
  (let [geometry (three/BoxGeometry. 200 200 200)
        material (three/MeshStandardMaterial. (js-obj "color" 0xff0040 "wireframe" false))
        mesh (three/Mesh. geometry material)]
    (->RenderComponent {:object mesh, :material material, :geometry geometry})))

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
    (->RenderComponent {:object sprite, :material material})))
