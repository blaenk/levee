(ns levee.scgi
  (:require [necessary-evil.core :as xml-rpc]
            [clojure.string :refer [split join]])
  (:import (java.net Socket URI)
           (java.util Scanner)))

(defn- parse
  "parse an scgi response into ring-response format"
  [res]
  (let [[headers body] (split res #"\r\n\s*?\n" 2)
        parsed-headers (apply hash-map
                              (mapcat #(split % #": ") (split headers #"\r\n")))]
    {:headers parsed-headers :body body}))

(defn- build
  "construct an scgi request, merging in headers as needed"
  [{:keys [headers body]}]
  (let [combined-headers (apply concat ["CONTENT_LENGTH" (count body) "SCGI" 1]
                                       headers)
        formatted-headers (join "\u0000" combined-headers)]
    (str (count formatted-headers) ":" formatted-headers "," body)))

(defn- request
  "perform an scgi request"
  [endpoint params]
  (let [url (URI. (str endpoint))
        socket (Socket. (.getHost url) (.getPort url))
        ins (.getInputStream socket)
        ous (.getOutputStream socket)
        scanner (.useDelimiter (Scanner. ins) "\\A")]
    (.write ous (.getBytes (build params)))
    (if (.hasNext scanner)
      (parse (.next scanner))
      "")))

(defn call
  "xml-rpc/call wrapper"
  [endpoint method & args]
  (xml-rpc/call* endpoint method args :post-fn request))

