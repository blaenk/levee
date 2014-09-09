(ns figwheel
  (:require [figwheel.client :as fw :include-macros true]
            [clojure.browser.repl]
            [levee.client.core :as core]
            [levee.client.common :as common]))

(fw/watch-and-reload
  :websocket-url (str (if (= (.-protocol js/location) "https:") "wss" "ws")
                      "://"
                      (.. js/window -location -hostname)
                      ":3449/figwheel-ws")
  :jsload-callback
    (fn []
      (core/render)
      (.log js/console "[FIGWHEEL] reloaded")))

