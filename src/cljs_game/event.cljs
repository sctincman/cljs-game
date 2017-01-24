(ns cljs-game.event
  (:require ))



(defn capture [event-name handler]
  (.addEventListener js/document (name event-name) handler false))

(defn raise [event-name & args]
  (let [event (js/CustomEvent. (name event-name) (clj->js {:detail args}))]
    (.dispatchEvent js/document event)))
