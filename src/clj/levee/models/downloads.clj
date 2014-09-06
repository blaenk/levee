(ns levee.models.downloads
  (:require [levee.rtorrent :as rtorrent]
            [levee.common :refer [base64-encode base64-decode]]
            [levee.models.users :as users]
            [me.raynes.fs :refer [base-name]]
            [ring.util.response :as response]))

(defn- get-progress [size completed]
  "calculate the percentage of a torrent"
  (if-not (= size 0)
    (* (/ (Long/parseLong completed) (Double/parseDouble size)) 100)
    0))

(defn- get-ratio [ratio]
  (/ (Long/parseLong ratio) 1000.0))

(defn- get-state [hashing open active complete]
  "determine a torrent's human-friendly state
   given a variety of rtorrent state values"
  (if (= hashing "1")
    "hashing"
    (if-not (= open "1")
      "closed"
      (if-not (= active "1")
        "stopped"
        (if-not (= complete "1")
          "downloading"
          "seeding")))))

(defn- construct-download [{:keys [hash_checking? open? active? multi_file?
                                   complete completed_chunks
                                   size_chunks hash name directory
                                   ratio message peers_accounted peers_complete
                                   up_rate down_rate up_total
                                   levee-uploader levee-locks
                                   levee-date-added]}]
  {:name name
   :state (get-state hash_checking? open? active? complete)
   :ratio (format "%.2f" (get-ratio ratio))
   :message message
   :progress (format "%.0f" (get-progress size_chunks completed_chunks))
   :hash (clojure.string/lower-case hash)
   :uploader (base64-decode levee-uploader)
   :date-added (base64-decode levee-date-added)
   :locks (cheshire.core/parse-string (base64-decode levee-locks))
   :directory (base-name directory)
   :multi_file? (= multi_file? "1")
   :leeches peers_accounted
   :seeders peers_complete
   :up_rate up_rate
   :down_rate down_rate
   :total_uploaded up_total})

(defn- construct-file [{:keys [size_bytes completed_chunks size_chunks
                               priority path path_components]}]
  {:path path
   :path_components path_components
   :size size_bytes
   :progress (format "%.0f" (get-progress size_chunks completed_chunks))
   :enabled (not= priority "0")})

(defn get-downloads []
  (let [downloads (rtorrent/torrents "main"
                    :get_name
                    :get_hash
                    :get_directory
                    :get_ratio
                    :get_message
                    :get_complete
                    :is_multi_file
                    :is_hash_checking
                    :is_active
                    :is_open
                    :get_size_bytes
                    :get_size_chunks
                    :get_completed_chunks
                    :get_peers_accounted
                    :get_peers_complete
                    :get_up_rate
                    :get_down_rate
                    :get_up_total
                    :get_custom=levee-uploader
                    :get_custom=levee-locks
                    :get_custom=levee-date-added)]
    (map construct-download downloads)))

(defn get-files [hash]
  (map-indexed (fn [i f] (assoc (construct-file f) :id i))
               (rtorrent/files hash
                 :get_path
                 :get_path_components
                 :get_priority
                 :get_completed_chunks
                 :get_size_chunks
                 :get_size_bytes)))

;; TODO: reason here for creating map-producing rtorrent api, like korma
;; TODO: note that rtorrent/torrent doesn't work with get_custom=whatever
(defn get-download [hash]
  (let [download (rtorrent/torrent hash
                   :get_name
                   :get_directory
                   :get_hash
                   :get_complete
                   :get_ratio
                   :get_message
                   :is_multi_file
                   :is_hash_checking
                   :is_active
                   :is_open
                   :get_size_bytes
                   :get_size_chunks
                   :get_completed_chunks
                   :get_peers_accounted
                   :get_peers_complete
                   :get_up_rate
                   :get_down_rate
                   :get_up_total
                   [:get_custom "levee-uploader"]
                   [:get_custom "levee-locks"]
                   [:get_custom "levee-date-added"])
        download (construct-download download)
        files (get-files hash)]
    (assoc download :files files))
  )

;; extracting (?):
;;   path
;;   size

(defn commit-file-priorities [{:keys [route-params json-params]}]
  (let [hash (:hash route-params)
        priorities (map (fn [[id enabled?]] [id (if enabled? :normal :off)])
                        (get json-params "priorities"))]
    (rtorrent/set-file-priorities hash priorities)
    (response/response {:success true})))

(defn stop [hash]
  (rtorrent/call :d.stop hash)
  (response/response (get-download hash)))

(defn start [hash]
  (rtorrent/call :d.start hash)
  (response/response (get-download hash)))

(defn erase [hash req]
  (let [uploader (rtorrent/get-custom hash "levee-uploader")
        locks (cheshire.core/parse-string (rtorrent/get-custom hash "levee-locks"))
        current-user (users/current-user req)]
    (if (or (get (:roles current-user) :levee.auth/admin)
            (and
             (= uploader (:username current-user))
             (empty? locks)))
      (do
        (rtorrent/call :d.erase hash)
        (response/response (get-downloads)))
      (response/response {:error "lacks authorization"}))))

(defn lock-toggle [hash req]
  (let [locks (set (cheshire.core/parse-string (rtorrent/get-custom hash "levee-locks")))
        username (:username (users/current-user req))
        new-locks (if (get locks username)
                    (vec (disj locks username))
                    (vec (conj locks username)))]
    (rtorrent/set-custom hash "levee-locks" (cheshire.core/generate-string new-locks))
    (response/response new-locks)))

(defn load-torrent [{:keys [multipart-params] :as req}]
  (clojure.pprint/pprint multipart-params)
  (let [current-user (users/current-user req)]
    (apply rtorrent/load-torrent
           (get-in multipart-params ["file" :tempfile])
           (= "true" (get multipart-params "start"))
           (rtorrent/on-load (:username current-user)))
    (response/response {:success true})))

(defn load-magnet [{:keys [json-params] :as req}]
  (let [current-user (users/current-user req)]
    (apply rtorrent/load-magnet
           (get json-params "uri")
           (get json-params "start")
           (rtorrent/on-load (:username current-user)))
    (response/response {:success true})))

