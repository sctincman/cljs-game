(ns cljs-game.render.threejs.core
  (:require [cljs-game.render.core :as render]
            [cljs-game.render.threejs.sprite :as sprite]
            [cljs-game.render.threejs.texture :as texture]
            [threejs :as three]))

(defrecord ThreeJSRenderComponent [backend]
  render/IRenderable
  (prepare [this entity]
    (when (some? (:position entity))
      (set! (.-x (.-position (:object backend)))
            (get-in entity [:position :x]))
      (set! (.-y (.-position (:object backend)))
            (get-in entity [:position :y]))
      (set! (.-z (.-position (:object backend)))
            (get-in entity [:position :z])))))

(defn ^:export create-program [gl vertex-source fragment-source]
  (let [program (.createProgram gl)
        vertex-shader (.createShader gl (.-VERTEX_SHADER gl))
        fragment-shader (.createShader gl (.-FRAGMENT_SHADER gl))]
    (.shaderSource gl vertex-shader vertex-source)
    (.shaderSource gl fragment-shader fragment-source)
    (.compileShader gl vertex-shader)
    (.compileShader gl fragment-shader)
    (.attachShader gl program vertex-shader)
    (.attachShader gl program fragment-shader)
    (.linkProgram gl program)
    program))

(defrecord ^:export ThreeJSBackend [renderer scene objects]
  render/IRenderBackend
  (add-to-backend [this renderable]
    (.add scene (get renderable :object))
    renderable)
  (render [this entities camera]
    (let [entities (doall (render/prepare-scene this (render/animate entities 0.0)))
          cam (:object (first (:renders (get entities camera))))]
      (.render renderer scene cam)
      entities))
  (prepare-scene [this entities]
    (reduce-kv (fn [entities id entity]
                 (when (render/renderable? entity)
                   (loop [render (first (:renders entity))
                          the-rest (rest (:renders entity))]
                     (render/prepare render entity)
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
      (->ThreeJSRenderComponent {:object mesh, :material material, :geometry geometry})))
  (create-sprite [this texture]
    (let [base-texture (if (satisfies? render/ITextureAtlas texture)
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
      (sprite/->ThreeJSSprite sprite texture nil 1.0 1.0)))
  (create-sprite2 [this texture]
    (let [vertices (js/Float32Array.
                    [-0.5, -0.5,  0.0,
                      0.5, -0.5,  0.0,
                      0.5,  0.5,  0.0,
                     -0.5,  0.5,  0.0])
          uvs (js/Float32Array.
               [0.0, 0.0,
                1.0, 0.0,
                1.0, 1.0,
                0.0, 1.0])
          indices (js/Uint16Array.
                   [0, 1, 2,
                    0, 2, 3])
          base-texture (if (satisfies? render/ITextureAtlas texture)
                         (:texture texture)
                         texture)
          js-texture (:texture base-texture)
          geometry (three/BufferGeometry.)
          material (three/MeshBasicMaterial.
                    (js-obj "map" js-texture
                            "wireframe" false
                            "transparent" true))]
      (.addAttribute geometry "position" (three/BufferAttribute. vertices 3))
      (.addAttribute geometry "uv" (three/BufferAttribute. uvs 2))
      (.setIndex geometry (three/BufferAttribute. indices 1))
      (.computeBoundingBox geometry)
      (let [mesh (three/Mesh. geometry material)]
        (set! (.-x (.-scale mesh)) (:width base-texture))
        (set! (.-y (.-scale mesh)) (:height base-texture))
        (.add scene mesh)
        (->ThreeJSRenderComponent {:object mesh, :material material, :geometry geometry})))))

(defn ^:export create-threejs-backend! []
  (let [scene (three/Scene.)
        renderer (three/WebGLRenderer.)
        light (three/AmbientLight. 0xffffff)
        light2 (three/PointLight. 0xffffff 2 0)]
    (.add scene light)
    (set! (.-background scene) (three/Color. 0x6c6c6c))
    (.set (.-position light2) 300 300 400)
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
                              (texture/->ThreeJSTexture
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
