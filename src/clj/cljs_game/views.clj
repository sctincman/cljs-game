(ns cljs-game.views
  (:require [clojure.string :as str]
            [hiccup.page :as hic-p]))

(defn gen-page-head
  "Generates common headers given a page-title, returned as a hiccup style vector."
  [title is-debug]
  [:head
   [:title (str title)]
   (hic-p/include-css "/css/styling.css")
   (if is-debug
     (hic-p/include-js "/js/main.js")
     (hic-p/include-js "/js/main.min.js"))])

(def footer-bar
  "Generates the footer common to the application. Returns a hiccup style vector."
  [:div#footer-bar
   [:footer.banner.banner-bottom
    "This work licensed under a "
    [:a {:href "http://creativecommons.org/licenses/by/4.0/"}
     "Creative Commons Attribution 4.0 International License"]]])

(defn home-page
  "View for the home page. Returns hiccup formatted HTML5."
  [is-debug]
  (hic-p/html5
    (gen-page-head "Home" is-debug)
    [:body
     [:h1 (str "Home" (when is-debug (str " (Debug): " is-debug)))]
     [:p "CLJS webapp game thing."]
     [:canvas {:id "front-buffer"}]
     footer-bar]
    [:script
     "document.onreadystatechange = function () {
        if (document.readyState === \"complete\") {
          cljs_game.core.init_game()
        }
      }"]))
