(ns cljs-game.input
  (:require [cljs-game.entity :as ecs]
            [cljs-game.signals :as s]))

(enable-console-print!)

;; TODO, mechanism to removeEventListener? Grr Javascript wants the original func object
;;; Possibly, store the anon func as the tag in the outsignal? Otherwise make another field in signal :/
;;; Optionally debounce? (eg, weird repeat behavior on Linux)
(defonce keyevents
  (let [out-signal (s/signal nil "keyboard")]
    (.addEventListener
     js/document
     "keydown"
     (fn [event]
       (s/propagate out-signal
                  {:key (.-key event),
                   :repeat (.-repeat event),
                   :press :down})))
    (.addEventListener
     js/document
     "keyup"
     (fn [event]
       (s/propagate out-signal
                  {:key (.-key event),
                   :repeat (.-repeat event),
                   :press :up})))
    out-signal))

(def ^:export keyboard
  "A signal generated from keyboard events. Events are maps with fields for key, repeat, and whether it is a keydown/keyup event."
  keyevents)


(defrecord ^:export InputComponent [keymap state])

;;placeholder, right state behavior here to figure out how to structure the rest
;; How to get current state?
;; on-exit (call before calling next state function)
;; can emit (:turning-right-to-left, etc)
;; possibly new signal? add a duration to the state?
;;;; or, just have time as a second input signal,
(defn movement-states [command-signal]
  (let [out-signal (s/signal nil "input-movement-fsm")]
    (letfn [(enter-moving-right []
              ;;on-enter behavior
              (s/propagate out-signal :moving-right)
              (s/watch command-signal
                     (:tag out-signal)
                     (fn [tag old new]
                       (condp = new
                         :left (enter-moving-left)
                         :down (enter-standing)
                         nil))))
            (enter-moving-left []
              (s/propagate out-signal :moving-left)
              (s/watch command-signal
                     (:tag out-signal)
                     (fn [tag old new]
                       (condp = new
                         :right (enter-moving-right)
                         :down (enter-standing)
                         nil))))
            (enter-standing []
              ;;on-enter behavior
              (s/propagate out-signal :standing)
              (s/watch command-signal
                     (:tag out-signal)
                     (fn [tag old new]
                       (condp = new
                         :left (enter-moving-left)
                         :right (enter-moving-right)
                         nil))))]
      (enter-standing)
      out-signal)))

;;ugh, might be time to research function-state?
(defn ^:export movement-fsm [input-signal]
  (letfn [(enter-standing []
            {:state :standing,
             :transition standing})
          (standing [event]
            (when (= :down (:press event))
              (condp = (:key event)
                :left (enter-moving-left)
                :right (enter-moving-right)
                nil)))
          
          (enter-moving-right []
            {:state :moving-right
             :transition moving-right})
          (moving-right [event]
            (if (= :down (:press event))
              (condp = (:key event)
                :left (enter-moving-left)
                nil)
              (when (= :right (:key event))
                (enter-standing))))
          
          (enter-moving-left []
            {:state :moving-left
             :transition moving-left})
          (moving-left [event]
            (if (= :down (:press event))
              (condp = (:key event)
                :right (enter-moving-right)
                nil)
              (when (= :left (:key event))
                (enter-standing))))]
    
    (s/foldp (fn [state input]
               (let [next-state ((:transition state) input)]
                 (if (some? next-state)
                   next-state
                   state)))
             {:state :standing, :transition standing}
             input-signal)))

(defn ^:export movement
  "Given a keymap and entity, add input-driven movement component to entity, and returns updated entity."
  [entity keymap]
  (let [input-signal (s/map (fn [event]
                              (update event :key keymap))
                            keyboard)]
    ;; check if exists?
    (-> entity
        (assoc :input keymap)
        (assoc :movement (movement-fsm input-signal)))))
