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

;; "An unbuffered signal encapsulating a current value backed by an `atom`.
(defrecord Signal [value tag]
  ISignal
  (watch [this target handler]
    (add-watch value target
               (fn [key ref old new]
                 (handler key old new))))
  (propagate [this new-value]
    (swap! value (fn [old new] new) new-value))
  (value [this]
    @value))

;; For now, let's do just single values, later update to have abuffer (but queue for value doesn't work, removing value will trigger watches)
;; TODO, keyword args? (want to set prefix, don't care about init
(defn ^:export signal
  "Creates a new, unbuffered `atom` based signal with an optional initial value (defaults to `nil`), and optional tag prefix."
  ([] (signal nil))
  ([init] (->Signal (atom init) (keyword (gensym "signal_"))))
  ([init prefix] (->Signal (atom init) (keyword (gensym prefix)))))

;; Watchers call the outputs!
;; Don't return values, return signals
(defn ^:export foldp
  "Fold from past. Returns a new `Signal` that is the result of reduceing over an input `Signal` with the supplied function. `f` is a reducing function that takes 2 arguments: an accumulated state (current value of output `Signal`), and the current value of the input `Signal`. The return value becomes the new value of the output `Signal`.`init` is a starting value for the output `Signal`. `in-signal` is the input `Signal`. Returns the output `Signal`, allowing others to subscribe and consume from it."
  [f init in-signal]
  (let [out-signal (signal init "foldp")]
    (watch in-signal (:tag out-signal)
           (fn [target old-state new-state]
             (propagate out-signal (f (value out-signal) new-state))))
    out-signal))

;; I have no idea what this would be used for, but could be something fun!
;(defn foldf [fn in-signal])

;; expand to arbitrary amounts of in-signals when I can figure out how to handle the asynchronous nature...
;;; When one signal arrives, but others are unchanging, what is the behavior?
;;;; 1) Output new value with all signal inputs as-is (one changes changes out)
;;;; 2) Wait for all signals to change (must keep track of all signals...)
(defn- map*
  "Returns a new signal that is the application of `f` to all the input signals. Must match the arity of the number of input signal."
  [f in-signal]
  (let [out-signal (signal nil "map")]
    (watch in-signal (:tag out-signal)
           (fn [target old-state new-state]
             (propagate out-signal (f new-state))))
    out-signal))

;; TODO, mechanism to removeEventListener? Grr Javascript wants the original func object
;;; Possibly, store the anon func as the tag in the outsignal? Otherwise make another field in signal :/
(defn ^:export keyboard
  "Returns a signal generated from keyboard events. Optionally accepts a function to transform the raw Javascript event before propagating."
  ([] (keyboard identity))
  ([transformer]
   (let [out-signal (signal nil "keyboard")]
     (.addEventListener
      js/document
      "keydown"
      (fn [event]
        (propagate out-signal (transformer event))))
     out-signal)))

(defn ^:export frames
  "Returns a signal that triggers when a new frame needs to be rendered, with the value of the absolute time. CLJS uses `requestAnimationFrame`."
  []
  (let [out-signal (signal (system-time) "frames")]
    (letfn [(callback [time]
              (propagate out-signal time)
              (.requestAnimationFrame js/window callback))]
      (callback (system-time))
      out-signal)))

;;TODO setTimeout version for an AS FAST AS POSSIBLE callback?
;;TODO special `Time` Signal that triggers every delay, but whose value is the current time at observation?
;;TODO handle to removeInterval...
(defn ^:export tick
  "Returns a signal that triggers every `delay` milliseconds. Defaults to 0 ms (as fast as possible). CLJS uses `setInterval` and is thus limited to a 10 ms minimum delay."
  ([] (tick 0.0))
  ([delay]
   (let [out-signal (signal (system-time) "time")]
     (.setInterval js/window
                   (fn [] (propagate out-signal (system-time)))
                   delay)
     out-signal)))

;; exports
(def ^:export map map*)
