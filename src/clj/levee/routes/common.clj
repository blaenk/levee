(ns levee.routes.common
  (:require [cemerick.friend :as friend]
            [levee.views.layout :as layout]
            [ring.util.response :as response]))

(defn handle-resource [req res]
  (if (= (get-in req [:params :accept]) "json")
    (response/response res)
    (layout/app :bootstrap res)))

