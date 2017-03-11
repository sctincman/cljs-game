(ns cljs-game.entity
  (:require ))

(defrecord ^:export Entity [identifier triggers behaviors])

(defn ^:export create-entity
  [prefix components]
  (assoc components :identifier (gensym prefix)))
