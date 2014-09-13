(ns levee.models.downloads
  (:require [levee.rtorrent :as rtorrent]
            [levee.common :refer [base64-encode base64-decode]]
            [levee.models.users :as users]
            [me.raynes.fs :as fs]
            [org.httpkit.server :refer :all]
            [levee.common :refer [conf]]
            [ring.util.response :as response]))

(defn- get-progress
  "calculate the percentage of a torrent"
  [completed size]
  (if-not (= size 0)
   (* (/ (Long/parseLong completed) (Double/parseDouble size)) 100)
    0))

(defn- get-ratio [ratio]
  (/ (Long/parseLong ratio) 1000.0))

(defn- get-state
  "determine a torrent's human-friendly state
   given a variety of rtorrent state values"
  [hashing open active complete]
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
                                   complete completed_bytes
                                   size_bytes hash name
                                   ratio message peers_accounted peers_complete
                                   up_rate down_rate up_total
                                   levee-uploader levee-locks
                                   levee-date-added]}]
  {:name name
   :state (get-state hash_checking? open? active? complete)
   :ratio (format "%.2f" (get-ratio ratio))
   :message message
   :completed_bytes (Long/parseLong completed_bytes)
   :size_bytes (Long/parseLong size_bytes)
   :progress (format "%.0f" (get-progress completed_bytes size_bytes))
   :hash (clojure.string/lower-case hash)
   :uploader (base64-decode levee-uploader)
   :date-added (base64-decode levee-date-added)
   :locks (cheshire.core/parse-string (base64-decode levee-locks))
   :multi_file? (= multi_file? "1")
   :leeches (Long/parseLong peers_accounted)
   :seeders (Long/parseLong peers_complete)
   :up_rate (Long/parseLong up_rate)
   :down_rate (Long/parseLong down_rate)
   :total_uploaded (Long/parseLong up_total)})

(defn- construct-file [{:keys [size_bytes completed_chunks size_chunks
                               priority path path_components]}]
  {:path path
   :path_components path_components
   :name (last path_components)
   :size (Long/parseLong size_bytes)
   :progress (format "%.0f" (get-progress completed_chunks size_chunks))
   :enabled (not= priority "0")
   :extracted false})

(defn get-downloads []
  (let [downloads (rtorrent/torrents "main"
                    :get_name
                    :get_hash
                    :get_complete
                    :get_ratio
                    :get_message
                    :is_multi_file
                    :is_hash_checking
                    :is_active
                    :is_open
                    :get_size_bytes
                    :get_completed_bytes
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

(defn get-extracted [hash]
  (let [[[basepath] [name] [multi_file?]] (rtorrent/multicall
                                      [:get_directory]
                                      [:d.get_name hash]
                                      [:d.is_multi_file hash])
        directory (fs/file basepath name)
        cut-off (inc (count (.getPath directory)))
        path (fs/file directory "extract")
        extracting? (fs/exists? (fs/file directory ".extracting"))]
    (when (and (fs/exists? path) (= multi_file? "1"))
      (let [files (filter fs/file? (file-seq path))]
        (for [file files]
          (let [path (subs (.getPath file) cut-off)
                components (fs/split path)]
            {:path path
             :path_components components
             :name (last components)
             :size (fs/size (.getPath file))
             :progress (if extracting? 0 100)
             :enabled true
             :extracted true}))))))

;; TODO: reason here for creating map-producing rtorrent api, like korma
;; TODO: note that rtorrent/torrent doesn't work with get_custom=whatever
(defn get-download [hash]
  (let [download (rtorrent/torrent hash
                   :get_name
                   :get_hash
                   :get_complete
                   :get_ratio
                   :get_message
                   :is_multi_file
                   :is_hash_checking
                   :is_active
                   :is_open
                   :get_size_bytes
                   :get_completed_bytes
                   :get_peers_accounted
                   :get_peers_complete
                   :get_up_rate
                   :get_down_rate
                   :get_up_total
                   [:get_custom "levee-uploader"]
                   [:get_custom "levee-locks"]
                   [:get_custom "levee-date-added"])
        download (construct-download download)
        download (assoc download :files (get-files hash))
        download (assoc download :extracted-files (get-extracted hash))]
    download))

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
    (if (or (users/admin? req)
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

;; open?, close, websocket?, send!, on-receive, on-close
(defn downloads-feed [req]
  (with-channel req chan
    (loop []
      (Thread/sleep 5000)
      (when (open? chan)
        (send! chan (cheshire.core/generate-string (get-downloads)))
        (recur)))))

(defn download-feed [hash req]
  (with-channel req chan
    (loop [fast? false]
      (Thread/sleep (if fast? 1000 5000))
      (when (open? chan)
        (let [d (cheshire.core/generate-string (get-download hash))
              downloading? (or (= (:state d) "downloading")
                               (= (:state d) "hashing"))]
          (send! chan d)
          (recur downloading?))))))

(defn- sendfile
  "constructs an X-Accel-Redirect (sendfile) request for nginx"
  [file]
  (let [sendfile-path (.getPath (fs/file "/sendfile" file))
        base-name (fs/base-name file)]
    {:status 200
     :headers
       {"Content-Disposition" (str "filename=\"" base-name "\"")
        "Content-Type" ""
        "X-Accel-Redirect" sendfile-path}
     :body ""}))

(defn serve-file [file]
  (if (and (not (nil? (conf :sendfile)))
           (not= (conf :sendfile) "no"))
    (sendfile file)
    (response/file-response file {:root (rtorrent/call :get_directory)})))

