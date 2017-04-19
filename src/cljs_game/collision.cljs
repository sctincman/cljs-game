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

(defrecord ^:export AABB [center width height depth half-width half-height half-depth]
  IAABB
  (left [this] (- (:x center) half-width))
  (right [this] (+ (:x center) half-width))
  (bottom [this] (- (:y center) half-height))
  (top [this] (+ (:y center) half-height))
  (back [this] (- (:z center) half-depth))
  (front [this] (+ (:z center) half-depth))
  (mins [this] (v/vector (left this) (bottom this) (back this)))
  (maxs [this] (v/vector (right this) (top this) (front this))))

(defn ^:export add-aabb [entity offset width height depth]
  (assoc entity :aabb
         (->AABB offset width height depth
                (/ width 2.0) (/ height 2.0) (/ depth 2.0))))

(defn ^:export add-space
  "Spatial component. Stores a location based hash of entities with collision components."
  [entity bucket-size]
  (let [inverse (/ 1 bucket-size)]
    (-> entity
        (assoc :bucket (v/vector inverse inverse inverse))
        (assoc :cells (hash-map)))))

(defn pos->bucket
  [space position]
  (let [bucket (v/dot position
                    (:bucket space))]
    {:x (int (:x bucket))
     :y (int (:y bucket))
     :z (int (:z bucket))}))

(defn ^:export spatial?
  "Does component have a position and collision components."
  [entity]
  (and (some? (:position entity))
       (some? (:aabb entity))) ;change to general collision, or make `collidable?` call
  )

(defn aabb-buckets [space entity]
  (let [start-point (pos->bucket space (v/add (:position entity)
                                              (mins (:aabb entity))))
        end-point (pos->bucket space (v/add (:position entity)
                                            (maxs (:aabb entity))))]
    (for [x (range (:x start-point) (inc (:x end-point)))
          y (range (:y start-point) (inc (:y end-point)))
          z (range (:z start-point) (inc (:z end-point)))]
      {:x x, :y y, :z z})))

;; For now, just construct from scratch
;;; Possible make a dissoc->assoc version later for scenes with large amounts of static pieces
(defn populate-space
  "Buckets entities by their position and collisions"
  [space entities]
  (assoc space :cells
         (reduce-kv (fn [cells id entity]
                      (if (spatial? entity)
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
                                                      (range (:z start-point) (inc (:z end-point)))))
                                            cells
                                            (range (:y start-point) (inc (:y end-point)))))
                                  cells
                                  (range (:x start-point) (inc (:x end-point)))))
                        cells))
                    (hash-map)
                    entities)))

(defn aabb-intersects? [first-aabb second-aabb]
  (and (some? first-aabb) (some? second-aabb)
       (and (<= (left first-aabb) (right second-aabb))
            (>= (right first-aabb) (left second-aabb)))
       (and (<= (bottom first-aabb) (top second-aabb))
            (>= (top first-aabb) (bottom second-aabb)))
       (and (<= (back first-aabb) (front second-aabb))
            (>= (front first-aabb) (back second-aabb)))))

(defn translate-aabb
  [aabb position]
  (update aabb :center v/add position))

(defn ^:export aabb-collision-pairs
  "Returns list of collision pairs"
  [space entities]
  (let [space (populate-space space entities)]
    (reduce-kv (fn [pairs cell children]
                 (loop [first-key (first children)
                        the-rest (rest children)
                        pairs pairs]
                     (if (pos? (count the-rest))
                       (recur (first the-rest) (rest the-rest)
                              (reduce (fn [pairs second-key]
                                        (let [first-entity (get entities first-key)
                                              second-entity (get entities second-key)]
                                          (if (aabb-intersects? (translate-aabb (:aabb first-entity)
                                                                                (:position first-entity))
                                                                (translate-aabb (:aabb second-entity)
                                                                                (:position second-entity)))
                                            (conj pairs [first-key second-key])
                                            pairs)))
                                      pairs
                                      the-rest))
                       pairs)))
               []
               (:cells space))))

(defn ^:export handle-collisions
  "Not sure yet"
  [entities]
  (reduce-kv (fn [new-entities id entity]
               (when (some? (:bucket entity))
                 (let [pairs (aabb-collision-pairs entity entities)]
                   (loop [pair (first pairs)
                          rest-pairs (rest pairs)]
                     (let [first-handler (get-in entities [(first pair) :collisions])
                           second-handler (get-in entities [(second pair) :collisions])]
                       (when (some? first-handler)
                         (s/propagate first-handler pair))
                       (when (some? second-handler)
                         (s/propagate second-handler pair)))
                     (when (pos? (count rest-pairs))
                       (recur (first rest-pairs) (rest rest-pairs))))))
               (assoc new-entities id entity))
             (hash-map)
             entities)
  entities)
