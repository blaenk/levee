(ns levee.jobs
  (:require
    [levee.common :refer [base64-encode base64-decode]]
    [levee.rtorrent :as rtorrent]
    [clj-time.coerce :refer [from-string]]
    [clj-time.core :refer [plus weeks now after?]]
    [clj-time.core :refer [interval now minutes from-now in-millis]]
    [me.raynes.fs :as fs]
    [clojure.tools.logging :as log]))

(defn expired-torrents []
  (let [torrents (rtorrent/torrents "main"
                   :get_hash
                   :get_custom=levee-date-added
                   :get_custom=levee-locks)
        transform #(-> %
                    (assoc :expires-on
                           (-> (:levee-date-added %)
                               (base64-decode)
                               (from-string)
                               (plus (weeks 1))))
                    (update-in [:levee-locks]
                               (comp cheshire.core/parse-string base64-decode)))
        torrents (map transform torrents)
        expired? (fn [t] (and
                           (after? (now) (:expires-on t))
                           (empty? (:levee-locks t))))]
        (filter expired? torrents)))

(defn prune []
  (while true
    (let [expired (expired-torrents)]
      (apply rtorrent/multicall
        (for [t expired]
          [:d.erase (:hash t)]))
      (log/info "pruned" (count expired) "torrents"))
    (Thread/sleep
      (-> (interval (now) (-> 10 minutes from-now)) in-millis))))

(defn stale []
  (while true
    (let [basepath (rtorrent/call :get_directory)
          rt_dirs (set (map #(fs/file basepath (:name %))
                            (rtorrent/torrents "main" :name)))
          dirs (set (fs/list-dir basepath))
          stale (clojure.set/difference dirs rt_dirs)
          stale-count (count stale)]
      (doseq [s stale]
        (when (fs/child-of? basepath s)
          (if (fs/directory? s)
            (fs/delete-dir s)
            (fs/delete s))))
      (when (> stale-count 0)
        (log/info "removed" stale-count "stale entities")))
    (Thread/sleep
      (-> (interval (now) (-> 30 minutes from-now)) in-millis))))

