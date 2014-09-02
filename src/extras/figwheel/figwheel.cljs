(ns figwheel
  (:require [figwheel.client :as fw :include-macros true]
            [clojure.browser.repl]
            [levee.client.core :as core]))

(fw/watch-and-reload
  :websocket-url (str "ws://" (.. js/window -location -hostname) ":3449/figwheel-ws")
  :jsload-callback
    (fn []
      (core/render)
      ;; (swap! core/app-state identity)
      (print "[FIGWHEEL] reloaded")))

