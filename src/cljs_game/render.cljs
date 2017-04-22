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

(defprotocol ^:export IRenderable
  (prepare [this entity] "Make any ugly backend mutations here"))

(defrecord RenderComponent [backend]
  IRenderable
  (prepare [this entity]
    (when (some? (:position entity))
      (set! (.-x (.-position (:object backend)))
            (get-in entity [:position :x]))
      (set! (.-y (.-position (:object backend)))
            (get-in entity [:position :y]))
      (set! (.-z (.-position (:object backend)))
            (get-in entity [:position :z])))))

(defprotocol ^:export ITexture
  "Abstract the details of a texture into this standard interface."
  (subtexture [this offset-x offset-y width height] "Returns a new texture that is a subsection of the parent.")
  (magnification-filter [this filter] "Sets the filter used when upscaling.")
  (minification-filter [this filter] "Sets the filter used when downscaling."))

(defprotocol ^:export ITextureAtlas
  "Reference subtextures in a parent texture"
  (defsub [this key offset-x offset-y width height] "Assign a key to a subregion of the texture")
  (getsub [this key] "Fetch subtexture by key"))

(defrecord ^:export ThreeJSTexture [texture submaps width height]
  ITexture
  (subtexture [this offset-x offset-y width height]
    (let [new-texture (.clone texture)]
      (set! (.-offset new-texture) (three/Vector2. offset-x offset-y))
      (set! (.-wrapS new-texture) three/RepeatWrapping)
      (set! (.-wrapT new-texture) three/RepeatWrapping)
      (set! (.-repeat new-texture) (three/Vector2. (/ width (.-width (.-image texture)))
                                                   (/ height (.-height (.-image texture)))))
      (assoc this :texture new-texture)))
  (magnification-filter [this filter]
    (set! (.-magFilter texture)
          (condp = filter
            :bilinear three/LinearFilter
            :linear three/NearestFilter))
    this)
  (minification-filter [this filter]
    (set! (.-minFilter texture)
          (condp = filter
            :nearest three/NearestFilter
            :nearest-mip-nearest three/NearestMipMapNearestFilter
            :nearest-mip-linear three/NearestMipMapLinearFilter
            :linear three/LinearFilter
            :linear-mip-nearest three/LinearMipMapNearestFilter
            :linear-mip-linear three/LinearMipMapLinearFilter))
    this)
  ITextureAtlas
  (defsub [this key offset-x offset-y width height]
    (let [sub (subtexture this offset-x offset-y width height)]
      (assoc submaps key sub)))
  (getsub [this key]
    (get submaps key)))

(defn ^:export sprite-sheet
  "Splices a texture into a regular atlas. Key'd by {x,y}"
  ([texture width height] (sprite-sheet texture v/zero width height))
  ([texture offset width height]
   ;;hmmm, maybe later
   texture))


;;...threeJS closes over this for us!
(defn ^:export load-texture!
  "Load a texture from a URI"
  [uri]
  (let [loader (three/TextureLoader.)
        texture (.load loader uri)]
    (->ThreeJSTexture texture {} nil nil)))

(defprotocol ^:export ISprite
  (key-texture [this key])
  (set-texture [this texture]))

(defrecord ^:export ThreeJSSprite [object texture atlas-key scale-x scale-y]
  ISprite
  (set-texture [this texture]
    (let [js-texture (:texture texture)]
      (set! (.-map (.-material object)) js-texture)
      (assoc this :texture texture)))
  (key-texture [this key]
    (let [js-texture (:texture (getsub texture key))]
      (set! (.-map (.-material object)) js-texture)
      (assoc this :atlas-key key)))
  IRenderable
  (prepare [this entity]
    (when (some? (:position entity))
      (set! (.-x (.-position (:sprite this)))
            (get-in entity [:position :x]))
      (set! (.-y (.-position (:sprite this)))
            (get-in entity [:position :y]))
      (set! (.-z (.-position (:sprite this)))
            (get-in entity [:position :z])))))

(defn ^:export add-sprite
  [entity texture]
  (let [material (three/SpriteMaterial. )
        sprite (three/Sprite. material)]

    (let [width (.-width (.-image texture))
          height (.-height (.-image texture))]
      (set! (.-map material) (:texture texture))
      (set! (.-lights material) true)
      (set! (.-x (.-scale sprite)) width)
      (set! (.-y (.-scale sprite)) height)
      (set! (.-needsUpdate material) true))
    (update entity :renders conj (->ThreeJSSprite sprite texture nil 1.0 1.0))))


(defrecord ScaleComponent [x y z])

(defn renderable? [entity]
  (and (:renders entity)
       (pos? (count (:renders entity)))
       (:position entity)))

(defprotocol ^:export RenderBackend
  "Abstract the details of a backend into this standard interface."
  (render [this entities camera] "Using the list of entities, render and display the scene. Camera is the key of the camera entity to use for rendering.")
  (add-to-backend [this renderable] "Ensure backend is aware of an entity's render-components. Eg for Three.js this does Scene.add(mesh).")
  (prepare-scene [this entities] "Perform backend specific actions needed before rendering.")
  (test-cube [this])
  (sprite [this texture])
  (create-sprite! [this url width height]))

(defrecord ^:export ThreeJSBackend [renderer scene objects]
  RenderBackend
  (add-to-backend [this renderable]
    (.add scene (get renderable :object))
    renderable)
  (render [this entities camera]
    (let [entities (doall (prepare-scene this entities))
          cam (:object (first (:renders (get entities camera))))]
      (.render renderer scene cam)
      entities))
  (prepare-scene [this entities]
    (reduce-kv (fn [entities id entity]
                 (when (renderable? entity)
                   (loop [render (first (:renders entity))
                          the-rest (rest (:renders entity))]
                     (prepare render entity)
                     (when-not (empty? the-rest)
                       (recur (first the-rest) (rest the-rest)))))
                 entities)
               entities
               entities))
  (test-cube [this]
    (let [geometry (three/BoxGeometry. 200 200 200)
          material (three/MeshStandardMaterial. (js-obj "color" 0xff0040 "wireframe" false))
          mesh (three/Mesh. geometry material)]
      (.add scene mesh)
      (->RenderComponent {:object mesh, :material material, :geometry geometry})))
  (sprite [this texture]
    (let [material (three/SpriteMaterial. )
          sprite (three/Sprite. material)]
      (let [width (.-width (.-image texture))
            height (.-height (.-image texture))]
        (set! (.-map material) (:texture texture))
        (set! (.-lights material) true)
        (set! (.-x (.-scale sprite)) width)
        (set! (.-y (.-scale sprite)) height)
        (set! (.-needsUpdate material) true))
      (.add scene sprite)
      (->ThreeJSSprite sprite texture nil 1.0 1.0)))

  (create-sprite!
    [this image-url width height]
    (let [material (three/SpriteMaterial. )
          sprite (three/Sprite. material)
          loader (three/TextureLoader.)]
      (.load loader image-url
             (fn [texture]
               (let [width (if (some? width) width (.-width (.-image texture)))
                     height (if (some? height) height (.-height (.-image texture)))]
                 (set! (.-offset texture) (three/Vector2. 0.0 0.0))
                 (set! (.-wrapS texture) three/RepeatWrapping)
                 (set! (.-wrapS texture) three/RepeatWrapping)
                 (set! (.-repeat texture) (three/Vector2. (/ width (.-width (.-image texture)))
                                                          (/ height (.-height (.-image texture)))))
                                        ;(set! (.-magFilter texture) three/NearestFilter)
                 (set! (.-map material) texture)
                 (set! (.-lights material) true)
                 (set! (.-x (.-scale sprite)) width)
                 (set! (.-y (.-scale sprite)) height)
                 (set! (.-needsUpdate material) true))))
      (.add scene sprite)
      (->RenderComponent {:object sprite, :material material}))))

(defprotocol ^:export ICamera
  "Abstract camera"
  (look-at [this point] "Rotate camera to look at a point"))

(defrecord ^:export ThreeJSCamera [object]
  IRenderable
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
        entity (-> (ecs/->Entity (gensym "perspective-camera") {} {})
                   (assoc :position (v/vector 0 0 900))
                   (assoc :rotation nil)
                   (assoc :camera {:type :perspective}))]
    (update entity :renders conj
            (->ThreeJSCamera camera))))

(defn ^:export ThreeJSOrthoCamera [left right top bottom near far]
  (let [camera (three/OrthographicCamera. left right top bottom near far)
        entity (-> (ecs/->Entity (gensym "ortho-camera") {} {})
                   (assoc :position (v/vector 0 0 900))
                   (assoc :rotation nil)
                   (assoc :camera {:type :orthographic}))]
    (update entity :renders conj
            (->ThreeJSCamera camera))))

(defn ^:export create-threejs-backend! []
  (let [scene (three/Scene.)
        renderer (three/WebGLRenderer.)
        light (three/AmbientLight. 0x404050)
        light2 (three/PointLight. 0xcbdbdf 2 0)]
    (.add scene light)
    (set! (.-background scene) (three/Color. 0x6c6c6c))
    (.set (.-position light2) 300 300 300)
    (.add scene light2)
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (js/document.body.appendChild (.-domElement renderer))
    (->ThreeJSBackend renderer scene [light light2])))
