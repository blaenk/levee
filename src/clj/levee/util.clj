(ns levee.util
  (:require
    [clojure.data.codec.base64 :as b64]))

(defn base64-encode [value]
  (String. (b64/encode (.getBytes value))))

(defn base64-decode [value]
  (String. (b64/decode (.getBytes value))))

