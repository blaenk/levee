(ns levee.common
  (:require
    [environ.core :refer [env]]
    [clojure.data.codec.base64 :as b64]))

(defn base64-encode [value]
  (String. (b64/encode (.getBytes value))))

(defn base64-decode [value]
  (String. (b64/decode (.getBytes value))))

(defn dev? [] (= (env :env) "dev"))

