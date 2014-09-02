(ns levee.config
  (:refer-clojure
    :exclude [get set])
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [crypto.random :as random]))

(def path "conf.clj")

(defn- create-secret [data]
  (update-in data [:secret]
    #(if (clojure.string/blank? %)
       (subs (random/url-part 16) 0 16)
       %)))

(defn read-configuration []
  (-> (edn/read-string (slurp path))
      (create-secret)))

(def data (atom (read-configuration)))

(add-watch data :write-configuration
  (fn [key reference old-val new-val]
    (spit path (with-out-str (pprint new-val)))))

(defn get [ks]
  (get-in @data ks))

(defn set [ks val]
  (swap! data update-in ks val))

