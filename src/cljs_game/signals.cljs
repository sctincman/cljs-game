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
    (reset! value new-value))
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
  [f in-signal]
  (let [out-signal (signal nil "map")]
    (watch in-signal (:tag out-signal)
           (fn [target old-state new-state]
             (propagate out-signal (f new-state))))
    out-signal))

;; Hmm, how best to handle the tag for watching? Currently use true-signal, but I don't like decoupling them and relying on one.
(defn ^:export split
  "Splits signal by `pred`. Returns two signals, one for values that pass, and one for those that fail. Return value is a map {:true true-signal, :false false-signal}, that can be destructured."
  [pred in-signal]
  (let [true-signal (signal nil "split-true")
        false-signal (signal nil "split-false")]
    (watch in-signal (:tag true-signal)
           (fn [target old-state new-state]
             (if (pred new-state)
               (propagate true-signal new-state)
               (propagate false-signal new-state))))
    {:true true-signal, :false false-signal}))

(defn- filter*
  [pred in-signal]
  (let [out-signal (signal nil "filter")]
    (watch in-signal (:tag out-signal)
           (fn [target old-state new-state]
             (when (pred new-state)
               (propagate out-signal new-state))))
    out-signal))

(defn ^:export route
  "Given an input-signal, and sequence of predicates, returns a sequence of output-signals, each triggered when their predicate is true. Predicates are of arity 3, receving the target tag, old-state, and new-state from the input-signal, returning true if the signal should trigger the corresponding output-signal."
  [input-signal & rest-args]
  (let [routes (map (fn [pred]
                      (when (fn? pred)
                        {:signal (signal nil (str "route-" pred))
                         :pred pred}))
                    rest-args)]
    (watch input-signal (:tag (:signal (first routes)))
           (fn [target old-state new-state]
             (map (fn [{signal :signal pred :pred}]
                    (when (pred target old-state new-state)
                      (propagate signal new-state)))
                  routes)))
    routes))

(defn route*
  [input-signal & rest-args]
  (map (fn [pred]
         (when (fn? pred)
           (filter* pred input-signal)))
       rest-args))

;; TODO, mechanism to removeEventListener? Grr Javascript wants the original func object
;;; Possibly, store the anon func as the tag in the outsignal? Otherwise make another field in signal :/
;; TODO listen for keypress/keyup events
;;; Optionally debounce? (eg, weird repeat behavior on Linux)
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

(defn ^:export timed
  "Returns a signal that emits the time when the input-signal triggers."
  [trigger-signal]
  (let [out-signal (signal 0.0 "timed")]
    (watch trigger-signal (:tag out-signal)
           (fn [target old-state new-state]
             (propagate out-signal (system-time))))
    out-signal))

(defn ^:export delta-time
  "Returns a signal that emits the change in a time-signal."
  [time-signal]
  (let [out-signal (signal 0.0 "delta-t")]
    (watch time-signal (:tag out-signal)
           (fn [target old-state new-state]
             (propagate out-signal (- new-state old-state))))
    out-signal))

;; how much time has passed between triggers?
(comment (defn dt [in-signal]
           (delta-time (timed in-signal))))

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
(def ^:export map
  "Returns a new signal that is the application of `f` to all the input signals. Must match the arity of the number of input signal."
  map*)

(def ^:export filter
  "Returns a new signal that with values from `in-signal` that satisfy `pred`."
  filter*)
