(ns cljs-game.render
  (:require [cljs-game.ecs :as ecs]
            [threejs :as three]))

(defrecord RenderComponent [mesh material geometry])

;; Hmm, we could use instance? but this works
(defn renderable? [entity]
  (and (:render-component (:components entity))
       (:position-component (:components entity))))

(defn prepare-scene [entities]
  (let [renderables (filter renderable? entities)]
    (doseq [renderable renderables]
      (set! (.-x (.-position (:mesh (:render-component (:components renderable)))))
            (:x (:position-component (:components renderable))))
      (set! (.-y (.-position (:mesh (:render-component (:components renderable)))))
            (:y (:position-component (:components renderable)))))))

(defn render [renderer scene camera entities]
  (prepare-scene entities)
  (.render renderer scene camera))

(defn test-cube [scene]
  (let [geometry (three/BoxGeometry. 200 200 200)
        material (three/MeshStandardMaterial. (js-obj "color" 0xff0040 "wireframe" false))
        mesh (three/Mesh. geometry material)
        render-component (->RenderComponent mesh material geometry)
        position-component (ecs/->PositionComponent 0 0)
        entity (ecs/->Entity 42 {:render-component render-component
                                 :position-component position-component})]
    (.add scene mesh)
    entity))
