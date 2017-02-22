(ns cljs-game.state
  (:require [cljs-game.signals :as s]))

(enable-console-print!)

(defprotocol ^:export IState
  "Respresents a state in a finite-state machine"
  (react [this event])
  (transition [this new-state on-exit on-enter]))

(defrecord State [state-name transitions]
  IState
  (react [this event])
  (transition [this new-state on-exit on-enter]))

;; FSM,
