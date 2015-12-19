(ns levee.rtorrent
  (:require [levee.scgi :as scgi]
            [levee.common :refer [base64-encode base64-decode conf]]
            [necessary-evil.core :as xml-rpc]
            [bencode.core :as bencode]
            [bencode.metainfo.reader
             :refer [torrent-info-hash-str
                     parse-metainfo]]
            [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.pprint :as pp]
            [clj-time.core :refer [now]]
            [clj-time.coerce :refer [to-string]]
            [environ.core :refer [env]])
  (:import java.net.URI
           org.apache.commons.io.IOUtils))

;; NOTE: single calls for resources
;;         - file    infohash:f{id} <- f.get_id zero-based
;;         - tracker infohash:t{id} <- t.get_id zero-based
;;         - peer    infohash:p{id} <- p.get_id (hash str)

;; NOTE: xmlrpc calls that consist of equal signs, such as
;;       view.persistent = some_view
;;       the equal sign essentially should be an empty string
;;       e.g. view.persistent "" "some_view"

;; RTORRENT CRASH
;;   (:system.multicall
;;     ({:methodName "f.path",
;;       :params ("85320F7A0686F7C5F7B7425FA94ECCD8A787E1A3" 0)}
;;      {:methodName "f.size_bytes",
;;       :params ("85320F7A0686F7C5F7B7425FA94ECCD8A787E1A3" 0)}))

(def endpoint
  (str "scgi://" (conf :rtorrent)))

(defn call
  "helper function for calling rtorrent with predefined endpoint"
  [& args]
  (if (= (.getScheme (URI. endpoint)) "scgi")
    (apply scgi/call endpoint args)
    (apply xml-rpc/call endpoint args)))

(defn- multicall-spec
  "construct a multicall call specification"
  [call]
  (if-not (sequential? call)
    {:methodName (name call) :params []}
    (let [[method & args] call
          method-name (name method)]
      (if args
        {:methodName method-name :params args}
        {:methodName method-name :params []}))))

(defn multicall
  "perform a multicall"
  [& calls]
  (call :system.multicall (map multicall-spec calls)))

(defn- format-calls [calls prefix multicall]
  (map (fn [c]
         (let [str-call (name (if-not (or (vector? c) (seq? c)) c (first c)))
               formatted (str prefix str-call
                           (when (and multicall (= -1 (.indexOf str-call "=")))
                             "="))]
           (if-not (vector? c) formatted (concat [formatted (rest c)]))))
       calls))

(defn- prettify-calls [calls]
  (let [dot #"\."
        is-prefix #"is_(.+)"
        get-prefix #"get_(.+)"
        meta-prefix #"(.+)=(.+)"]
    (map
      (fn [c]
        (if (or (vector? c) (seq? c))
          (keyword (get c 1))
          (-> (name (if-not (or (vector? c) (seq? c)) c (first c)))
             (clojure.string/replace dot "-")
             (clojure.string/replace is-prefix "$1?")
             (clojure.string/replace get-prefix "$1")
             (clojure.string/replace meta-prefix "$2")
             (keyword))))
      calls)))

(defn get-resource [resource args & calls]
  (let [res (name resource)
        plural (.endsWith res "s")
        resource->prefix {:torrent "d.", :file "f.", :peer "p.", :tracker "t."}
        prefix (resource->prefix
                 (if plural
                   (keyword (subs res 0 (dec (count res))))
                   resource))
        formatted (format-calls calls prefix plural)
        pretty-calls (prettify-calls calls)]
    (if-not plural
      (let [argumented (map (fn [c]
                              (if-not (or (vector? c) (seq? c))
                                (into [c] args)
                                (let [[x & xs] c
                                      res (apply concat [x] args xs)]
                                  res))) formatted)
            results (flatten (apply multicall argumented))]
        (zipmap pretty-calls results))
      (let [results (apply call (str prefix "multicall") (flatten [args formatted]))]
        (map #(zipmap pretty-calls %) results)))))

(defmacro torrent  [hash & args] `(get-resource :torrent  ~[hash] ~@args))
(defmacro torrents [view & args] `(get-resource :torrents ~[view] ~@args))

(defmacro file  [hash id & args] `(get-resource :file  ~[hash id] ~@args))
(defmacro files [hash & args]    `(get-resource :files ~[hash 0]  ~@args))

(defmacro peer  [hash id & args] `(get-resource :peer  ~[hash id] ~@args))
(defmacro peers [hash & args]    `(get-resource :peers ~[hash 0]  ~@args))

(defmacro tracker  [hash id & args] `(get-resource :tracker  ~[hash id] ~@args))
(defmacro trackers [hash & args]    `(get-resource :trackers ~[hash 0]  ~@args))

;; locks and date-added are added by rtorrent
(defn on-load [uploader]
  (let [uploader (base64-encode uploader)]
    [(str "d.custom.set=levee-uploader," uploader)]))

;; check (important because apparently bad stuff happens if add existing)
;;   view_list
;;
;; add a view
;;   view_add v
;;
;; update view
;;   view_filter d.views.has=v
;;
;; add torrent to view
;;   d.views.push_back_unique v
;;
;; remove torrent from view
;;   d.views.remove v
;;
;; can also do view.persistent
;;   view.persistent "" v

(defn disable-files-for-non-started [start? commands]
  ;; if not starting, turn off all files
  ;; this is a convenience because if we didn't start it, chances
  ;; are that we don't want to download all of the files, only some of them
  ;; this makes it easier to only select the files we want. equivalent to:
  ;;
  ;; (multicall
  ;;  [:f.multicall "DDFC7BCBB9A5BDEDD6E021CE94AC4FBF0B066A23" +0 "f.set_priority=1"]
  ;;  [:d.update_priorities "DDFC7BCBB9A5BDEDD6E021CE94AC4FBF0B066A23"])

  (if (not start?)
    (concat commands
            ["f.multicall=,,f.set_priority=0"
             "d.update_priorities="])
    commands))

(defn load-magnet
  "loads a magnet uri"
  [uri start? & commands]
  (let [method (if start? "load_start" "load")]
    (if commands
      (apply call method uri commands)
      (call method uri))))

(defn load-torrent
  "loads a torrent file"
  [file start? & commands]
  (let [contents (IOUtils/toByteArray (io/input-stream file))
        metadata (parse-metainfo contents)
        infohash (torrent-info-hash-str metadata)
        method (if start? "load_raw_start" "load_raw")
        commands (disable-files-for-non-started start? commands)]
    (if commands
      (apply call method contents commands)
      (call method contents))
    infohash))

(defn set-file-priorities
  "apply a map of file indices to priorities"
  [hash priorities]
  (let [name->priority {:off 0, :normal 1, :high 2}
        priority-calls (map (fn [[k v]]
                              [:f.set_priority (str hash ":f" k) (name->priority v)])
                            priorities)
        request (concat priority-calls [[:d.update_priorities hash]])]
    (apply multicall request)))

(defn set-custom
  "set a custom key-value pair for a torrent
   the value is base64 encoded to avoid escaping issues"
  [hash key value]
  (call :d.custom.set hash key (base64-encode value)))

(defn get-custom
  "get a custom key-value pair for a torrent
   the value is base64 encoded to avoid escaping issues"
  [hash key]
  (base64-decode (call :d.custom hash key)))

