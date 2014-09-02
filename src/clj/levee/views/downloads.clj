(ns levee.views.downloads
  (:require [levee.views.layout :as layout]
            [levee.rtorrent :as rtorrent]
            [hiccup.element :as el]))

(defn- download-item [download]
  [:li (el/link-to
         (str "/downloads/" (clojure.string/lower-case (:hash download)))
         (:name download))])

(defn downloads []
  (layout/app))

(defn download [hash]
  (layout/app :body [:p (str "this will be the page for " hash)]))

