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

(defrecord ^:export ThreeJSTexture [texture width height]
  ITexture
  (subtexture [this offset-x offset-y width height]
    (let [new-texture (.clone texture)]
      (set! (.-needsUpdate new-texture) true)
      (.set (.-offset new-texture)
            (/ offset-x (.-width (.-image new-texture)))
            (/ offset-y (.-height (.-image new-texture))))
      (set! (.-wrapS new-texture) three/RepeatWrapping)
      (set! (.-wrapT new-texture) three/RepeatWrapping)
      (.set (.-repeat new-texture)
            (/ width (.-width (.-image new-texture)))
            (/ height (.-height (.-image new-texture))))
      (-> this
          (assoc :texture new-texture)
          (assoc :width width)
          (assoc :height height))))
  
  (magnification-filter [this filter]
    (set! (.-magFilter texture)
          (condp = filter
            :linear three/LinearFilter
            :nearest three/NearestFilter))
    (set! (.-needsUpdate texture) true)
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
    (set! (.-needsUpdate texture) true)
    this))

(defrecord ^:export ThreeJSTextureAtlas [texture submaps]
  ITexture
  (subtexture [this offset-x offset-y width height]
    (assoc this :texture (subtexture texture offset-x offset-y width height)))
  (magnification-filter [this filter]
    (assoc this :texture (magnification-filter texture filter)))
  (minification-filter [this filter]
    (assoc this :texture (minification-filter texture filter)))
  ITextureAtlas
  (defsub [this key offset-x offset-y width height]
    (let [sub (subtexture texture offset-x offset-y width height)]
      (assoc-in this [:submaps key] sub)))
  (getsub [this key]
    (get submaps key)))

(defn ^:export sprite-sheet
  "Splices a texture into a regular atlas. Key'd by {x,y}"
  ([texture width height] (sprite-sheet texture v/zero width height))
  ([texture offset width height]
   (let [texture (magnification-filter texture :nearest)
         atlas (->ThreeJSTextureAtlas texture {})
         stride (int (/ (.-width (.-image (:texture texture))) width))
         rise (int (/ (.-height (.-image (:texture texture))) height))]
     (reduce (fn [atlas key]
               (defsub atlas key
                 (+ (:x offset) (* width (:x key)))
                 (+ (:y offset) (* height (:y key)))
                 width height))
             atlas
             (for [x (range 0 stride)
                   y (range 0 rise)]
               {:x x, :y y})))))

(defprotocol ^:export ISprite
  (key-texture [this key])
  (set-texture [this texture])
  (scale [this value]))

(defrecord ^:export ThreeJSSprite [object texture atlas-key scale-x scale-y]
  ISprite
  (set-texture [this texture]
    (let [js-texture (:texture texture)]
      (set! (.-map (.-material object)) js-texture)
      (set! (.-needsUpdate (.-material object)) true)
      (set! (.-x (.-scale object)) (:width texture))
      (set! (.-y (.-scale object)) (:height texture))
      (assoc this :texture texture)))
  (key-texture [this key]
    (if (satisfies? ITextureAtlas texture)
      (let [sub (getsub texture key)
            js-texture (:texture sub)]
        (set! (.-map (.-material object)) js-texture)
        (set! (.-needsUpdate (.-material object)) true)
        (set! (.-x (.-scale object)) (* scale-x (:width sub)))
        (set! (.-y (.-scale object)) (* scale-y (:height sub)))
        (assoc this :atlas-key key))
      this))
  (scale [this value]
    (let [sub (if (satisfies? ITextureAtlas texture)
                (getsub texture atlas-key)
                texture)]
      (set! (.-x (.-scale object)) (* value (:width sub)))
      (set! (.-y (.-scale object)) (* value (:height sub)))
      (-> this
          (assoc scale-x value)
          (assoc scale-y value))))
  IRenderable
  (prepare [this entity]
    (when (some? (:position entity))
      (set! (.-x (.-position (:object this)))
            (get-in entity [:position :x]))
      (set! (.-y (.-position (:object this)))
            (get-in entity [:position :y]))
      (set! (.-z (.-position (:object this)))
            (get-in entity [:position :z])))))

(defn ^:export add-animation [entity key animation]
  (assoc-in entity [:animations key] animation))

(defn ^:export set-animation [entity key]
  (let [animation (get (:animations entity) key)]
    (if (some? animation)
      (assoc entity :current-frame
             (s/map (fn [frames]
                      (first frames)) 
                    (s/foldp (fn [state step]
                               (let [remaining (rest state)]
                                 (if (empty? remaining)
                                   (:frames animation)
                                   (rest state))))
                             (:frames animation)
                             (s/tick (:duration animation)))))
      entity)))

(defn ^:export get-animation* [entity key]
  (let [animation (get-in entity [:animations key])]
    (s/map (fn [frames]
             (first frames)) 
           (s/foldp (fn [state step]
                      (let [remaining (rest state)]
                        (if (empty? remaining)
                          (:frames animation)
                          (rest state))))
                    (:frames animation)
                    (s/tick (:duration animation))))))

(defn ^:export state-animate [entity states signal]
  (assoc entity :current-frame
         (s/foldp (fn [current-animation state]
                    (let [new-state (:state state)
                          next-animation (get states new-state)]
                      (if (some? next-animation)
                        (get-animation* entity next-animation)
                        current-animation)))
                  (get-animation* entity (first (vals states)))
                  (:movement entity))))

(defn ^:export animate
  [entities delta-t]
  (reduce-kv (fn [entities id entity]
               (if (and (some? (:current-frame entity))
                        (not (empty? (:renders entity))))
                 (assoc entities id
                        (assoc entity :renders
                               (map (fn [renderer]
                                      (if (satisfies? ISprite renderer)
                                        (key-texture renderer (s/value (:current-frame entity)))
                                        renderer))
                                    (:renders entity))))
                 entities))
             entities
             entities))


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
  (create-sprite [this texture]))

(defrecord ^:export ThreeJSBackend [renderer scene objects]
  RenderBackend
  (add-to-backend [this renderable]
    (.add scene (get renderable :object))
    renderable)
  (render [this entities camera]
    (let [entities (doall (prepare-scene this (animate entities 0.0)))
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
  (create-sprite [this texture]
    (let [base-texture (if (satisfies? ITextureAtlas texture)
                         (:texture texture)
                         texture)
          js-texture (:texture base-texture) 
          material (three/SpriteMaterial.)
          sprite (three/Sprite. material)]
      (set! (.-map material) js-texture)
      (set! (.-lights material) true)
      (set! (.-needsUpdate material) true)
      (set! (.-x (.-scale sprite)) (:width base-texture))
      (set! (.-y (.-scale sprite)) (:height base-texture))
      (.add scene sprite)
      (->ThreeJSSprite sprite texture nil 1.0 1.0))))

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
                   (assoc :position (v/vector 0 0 700))
                   (assoc :rotation nil)
                   (assoc :camera {:type :perspective}))]
    (update entity :renders conj
            (->ThreeJSCamera camera))))

(defn ^:export ThreeJSOrthoCamera [left right top bottom near far]
  (let [camera (three/OrthographicCamera. left right top bottom near far)
        entity (-> (ecs/->Entity (gensym "ortho-camera") {} {})
                   (assoc :position (v/vector 0 0 700))
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

(defn ^:export load-texture! [loader [key uri] rest-uris resources start-func]
  (.load loader uri
         (fn [js-texture]
           (set! (.-magFilter js-texture) three/NearestFilter)
           (set! (.-needsUpdate js-texture) true)
           (let [accum (assoc resources key
                              (->ThreeJSTexture
                               js-texture
                               (.-width (.-image js-texture))
                               (.-height (.-image js-texture))))]
             (if (empty? rest-uris)
               (start-func (create-threejs-backend!) accum)
               (load-texture! loader (first rest-uris) (rest rest-uris) accum start-func))))))

(defn ^:export load-resources! [start-func]
  (let [loader (three/TextureLoader.)
        textures {:placeholder "assets/images/placeholder.png"
                  :deer "assets/images/deer.png"
                  :background "assets/images/test-background.png"
                  :forest-0 "assets/images/forest-0.png"
                  :forest-1 "assets/images/forest-1.png"
                  :forest-2 "assets/images/forest-2.png"
                  :forest-3 "assets/images/forest-3.png"}]
    (load-texture! loader (first textures) (rest textures) {} start-func)))
