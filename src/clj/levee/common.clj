(ns levee.common
  (:require
    [environ.core :refer [env]]
    [clojure.data.codec.base64 :as b64]
    [environ.core :refer [env]]))

(defn base64-encode [value]
  (String. (b64/encode (.getBytes value))))

(defn base64-decode [value]
  (String. (b64/decode (.getBytes value))))

(def ^:private defaults
  {:rtorrent "localhost:5000"
   :env "dev"
   :port "3000"})

(def conf [k]
  (get (merge defaults (env)) k))

(defn dev? [] (= (conf :env) "dev"))

