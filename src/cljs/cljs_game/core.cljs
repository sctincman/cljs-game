(ns cljs-game.core)

(set! (.-onreadystatechange js/document)
      (fn []
        (when (= (.-readyState js/document) "complete")
          (js/alert "Welcome!")
          (let [element (js/document.getElementById "front-buffer")
                context (.getContext element "2d")]
            (js/console.log context)
            (set! (.-fillStyle context) "green")
            (.fillRect context 10 10 100 100)
            ))))
