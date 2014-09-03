(ns levee.views.layout
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [hiccup.element :refer [javascript-tag link-to]]
            [cheshire.core :refer [generate-string]]
            [cemerick.austin.repls :refer [browser-connected-repl-js]]))

(defn- google-fonts [name & {:keys [sizes italics]}]
  (let [stem "http://fonts.googleapis.com/css?family="
        italics (when italics
                  (let [to-italicize (if (= italics :all) sizes italics)]
                    (map #(str % "italic") to-italicize)))
        sizes (clojure.string/join "," (concat sizes italics))
        safe-name (clojure.string/replace name " " "+")
        path (str stem safe-name ":" sizes)]
    [:link {:href path
            :rel "stylesheet"
            :type "text/css"}]))

(defn app [& {:keys [body bootstrap]}]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:link {:rel "icon" :type "image/png" :href "/img/favicon.png"}]
     [:title "LEVEE"]
     (include-css
       "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css"
       "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css"
       "/css/style.css")
     (google-fonts "Noto Sans" :sizes [400 700] :italics :all)]
    [:body
     [:div.upload-overlay]
     [:div#root.container body]
     (when bootstrap
       (javascript-tag
         (str "window.levee_bootstrap = " (generate-string bootstrap) ";")))
     (include-js
       "//code.jquery.com/jquery-1.11.0.min.js"
       "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js"
       "/js/moment.min.js"
       "/js/react-0.11.1.min.js"
       "/js/out/goog/base.js" ;; TODO: remove in release
       "/js/ZeroClipboard.min.js"
       "/js/main.js")
     ;; TODO: remove in release
     (javascript-tag "goog.require('levee.client.common');")
     (javascript-tag "goog.require('levee.client.components.upload');")
     (javascript-tag "goog.require('levee.client.components.downloads');")
     (javascript-tag "goog.require('levee.client.components.download');")
     (javascript-tag "goog.require('levee.client.core');")
     (javascript-tag "goog.require('figwheel');")
     [:script (browser-connected-repl-js)]
     ]))

(defn external [body]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:link {:rel "icon" :type "image/png" :href "/img/favicon.png"}]
     [:title "LEVEE"]
     (include-css
       "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css"
       "/css/style.css")
     (google-fonts "Noto Sans" :sizes [400 700] :italics :all)]
    [:body
     [:div#root.container body]]))

