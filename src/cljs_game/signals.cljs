(ns cljs-game.signals
  (:require ))

(enable-console-print!)

(defprotocol ^:export ISignal
  "A signal propogates values from a single producer to many consumers.
   Consumers register with watch (which mirrors `add-watch`). Producers
   use `propagate` to send a new value to consumers."
  (watch [this target handler] "Registers handler when signal value changes. `target` is a key used to identifier the consumer.")
  (propagate [this value] "Pushs a new value to consumers.")
  (value [this] "Returns the current value of the signal."))

(defrecord Signal [value]
  "An unbuffered signal encapsulating a current value. This implementation is backed by an `atom`"
  ISignal
  ;; Possibly have watch wrap the handler and not pass the ref, to avoid bad mutation
  ;; further more, consider how best to use the "key" in add-watch...
  (watch [this target handler]
    (add-watch value target handler))
  (propagate [this new-value]
    (swap! value (fn [old new] new) new-value))
  (value [this]
    @value))

;; For now, let's do just single values, later update to have abuffer (but queue for value doesn't work, removing value will trigger watches)
(defn ^:export signal
  "Creates a new, unbuffered signal with an optional initial value (defaults to `nil`)."
  ([] (signal nil))
  ([init] (->Signal (atom init))))

;; Watchers call the outputs!
;; Don't return values, return signals
(defn foldp
  "Fold from past. Creates a new `Signal` that reduces over an input `Signal`, outputting the accumulated results into an output `Signal`. `f` is a reducing function that takes 2 arguments: an accumulated state (current value of output `Signal`), and the current value of the input `Signal`. The return value becomes the new value of the output `Signal`.`init` is a starting value for the output `Signal`. `in-signal` is the input `Signal`. Returns the output `Signal`, allowing others to subscribe and consume from it."
  [f init in-signal]
  (let [out-signal (signal init)]
    (watch in-signal :any
           (fn [target signal old-state new-state]
             (propagate out-signal (f (value out-signal) new-state))))
    out-signal))

;; I have no idea what this would be used for, but could be something fun!
;(defn foldf [fn in-signal])

(defn keyboard
  "Returns a signal generated from keyboard events. Optionally accepts a function to transform the raw Javascript event before propagating."
  ([] (keyboard identity))
  ([transformer]
   (let [out-signal (signal)]
     (.addEventListener
      js/document
      "keydown"
      (fn [event]
        (propagate out-signal (transformer event))))
     out-signal)))
