(ns cljs-game.collision
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]
            [cljs-game.vector :as v]))

(defprotocol ^:export IAABB
  (left [this])
  (right [this])
  (top [this])
  (bottom [this])
  (back [this])
  (front [this])
  (mins [this])
  (maxs [this]))

(defrecord ^:export AABB [center height width depth half-width half-height half-depth]
  IAABB
  (left [this] (- (:x center) half-width))
  (right [this] (+ (:x center) half-width))
  (bottom [this] (- (:y center) half-height))
  (top [this] (+ (:y center) half-height))
  (back [this] (- (:z center) half-depth))
  (front [this] (+ (:z center) half-depth))
  (mins [this] (v/vector (left this) (bottom this) (back this)))
  (maxs [this] (v/vector (right this) (top this) (front this))))

(defn ^:export space
  "Spatial component. Stores a location based hash of entities with collision components."
  [bucket-size]
  (let [inverse (/ 1 bucket-size)]
    {:bucket (v/vector inverse inverse inverse)
     :cells (hash-map)}))

(defn pos->bucket
  [space position]
  (let [bucket (v/dot position
                    (:bucket space))]
    {:x (int (:x bucket))
     :y (int (:y bucket))
     :z (int (:x bucket))}))

(defn ^:export spatial?
  "Does component have a position and collision components."
  [entity]
  (and (some? (:position entity))
       (some? (:aabb entity))) ;change to general collision, or make `collidable?` call
  )

(defn aabb-buckets [entity]
  (let [start-point (pos->bucket space (v/add (:position entity)
                                              (mins (:aabb entity))))
        end-point (pos->bucket space (v/add (:position entity)
                                            (maxs (:aabb entity))))]
    (reduce (fn [cells x]
              (reduce (fn [cells y]
                        (reduce (fn [cells z]
                                  (conj cells {:x x, :y y, :z z}))
                                cells
                                (range (:z start-point) (:z end-point))))
                      cells
                      (range (:y start-point) (:y end-point))))
            []
            (range (:x start-point (:x end-point))))))

;; For now, just construct from scratch
;;; Possible make a dissoc->assoc version later for scenes with large amounts of static pieces
(defn populate-space
  "Buckets entities by their position and collisions"
  [space entities]
  (reduce-kv (fn [cells id entity]
               (let [start-point (pos->bucket space (v/add (:position entity)
                                                           (get-in entity [:aabb :center])
                                                           (v/vector (- (get-in entity [:aabb :half-width]))
                                                                     (- (get-in entity [:aabb :half-height]))
                                                                     (- (get-in entity [:aabb :half-depth])))))
                     end-point (pos->bucket space (v/add (:position entity)
                                                         (get-in entity [:aabb :center])
                                                         (v/vector (get-in entity [:aabb :half-width])
                                                                   (get-in entity [:aabb :half-height])
                                                                   (get-in entity [:aabb :half-depth]))))]
                 (reduce (fn [cells x]
                           (reduce (fn [cells y]
                                     (reduce (fn [cells z]
                                               (update cells {:x x, :y y, :z z} conj id))
                                             cells
                                             (range (:z start-point) (:z end-point))))
                                   cells
                                   (range (:y start-point) (:y end-point))))
                         cells
                         (range (:x start-point (:x end-point))))))
             (hash-map)
             entities))

;; likely a multimethod depending on collision shape pairs?
;;;; ugh vector addition
;;;;;; but, either here (occurs only for potentially colliding pairs
;;;;;; or! occurs everytime the friggin AABB moves...
(defn aabb-intersects? [first-aabb second-aabb]
  (and (and (<= (left first-aabb) (right second-aabb))
            (>= (right first-aabb) (left second-aabb)))
       (and (<= (bottom first-aabb) (top second-aabb))
            (>= (top first-aabb) (bottom second-aabb)))
       (and (<= (back first-aabb) (front second-aabb))
            (>= (front first-aabb) (back second-aabb)))))

(defn ^:export aabb-collision-pairs
  "Returns list of collision pairs"
  [entities]
  (let [collidables (filter spatial? entities)
        space (populate-space space collidables)]
    (reduce-kv (fn [pairs id first-entity]
                 (let [cell-keys (aabb-buckets first-entity)]
                   (reduce (fn [pairs cell-key]
                             (reduce (fn [pairs second-entity]
                                       (if (aabb-intersects? first-entity
                                                             (get entities second-entity))
                                         (conj pairs [first-entity
                                                      (get entities second-entity)])
                                         pairs))
                                     pairs
                                     (get space cell-key)))
                           pairs
                           cell-keys)))
               []
               collidables)))
