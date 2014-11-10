(ns levee.client.components.download
  (:require
    [cljs.core.async :refer [put! chan <!]]
    [om.core :as om :include-macros true]
    [sablono.core :as html :refer-macros [html defelem]]
    [levee.client.common :as common]
    [clojure.data :refer [diff]])
  (:require-macros
    [cljs.core.async.macros :refer [go-loop]]))

(defn- format-time [datetime how]
  (-> (.utc js/moment (js/Date. datetime))
      (.local)
      (.format
        (case how
          :short "l"
          :long "dddd, MMMM Do YYYY [at] h:mm:ss A"))))

(defn- expires-format [datetime]
  (-> (.utc js/moment (js/Date. datetime))
      (.local)
      (.add 2 "weeks")
      (.fromNow true)))

(defelem command-button [title icon handler]
  [:button.btn
    {:type "button"
     :data-toggle "tooltip"
     :data-placement "top"
     :data-original-title title
     :on-click handler}
     icon])

(defn file-tree [files]
  (letfn
    [(group-tree [tree]
       (clojure.set/rename-keys
         (group-by map? tree)
         {true :files false :folders}))

     (expand [files]
       (if (and (= (count files) 1)
                (= (count (:trail (first files))) 1))
         (group-tree (map :file files))
         (group-tree (mapv contract (group-by (comp first :trail) files)))))

     (consume-component [m]
       (if (= 1 (count (:trail m)))
         m
         (update-in m [:trail] (comp vec rest))))

     (contract [[path ms]]
       (if (and (= (count ms) 1)
                (= (count (:trail (first ms))) 1)
                (= path (first (:trail (first ms)))))
         (:file (first ms))

         (let [gen (expand (mapv consume-component ms))]
            [path gen])))]

    ["root" (expand (map #(hash-map :trail (:path_components %)
                                    :file %)
                         files))]))

(defn file-search-view [{:keys [file-count file-settings is-tree]
                         :as props} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [chan pattern]}]
      (html
        [:div.row
         [:div.col-lg-6.search.file-search
          [:div.input-group
           [:div.input-group-btn
            {:style (when-not is-tree {:display "none"})}
            [:button
             {:type "button"
              :class "btn btn-default dropdown-toggle"
              :data-toggle "tooltip"
              :data-placement "top"
              :title
                (if (:collapsed file-settings)
                  "expand"
                  "collapse")
              :data-original-title
                (if (:collapsed file-settings)
                  "expand"
                  "collapse")
              :on-click #(om/transact! file-settings :collapsed not)}
             [:span.glyphicon
              {:class
                (if (:collapsed file-settings)
                  "glyphicon-chevron-down"
                  "glyphicon-chevron-up")}]]]

           [:input.form-control
            {:type "text"
             :auto-focus "on"
             :placeholder "Search"
             :auto-complete "off"
             :value pattern
             :on-change
              (fn [e]
                (let [pat (.. e -target -value)]
                  (om/set-state! owner :pattern pat)
                  (put! chan [:pattern pat])))}]

           [:span.input-group-addon
            [:span.badge file-count]]]]]))))

(defn file-view [{:keys [file download editing]} owner]
  (reify
    om/IRender
    (render [_]
      (let [finished? (= (js/parseInt (:progress file)) 100)
            disabled? (not (:enabled file))
            file_name (:name file)
            disabled-tooltip (if disabled?
                               {:data-toggle "tooltip"
                                :data-placement "top"
                                :data-original-title "file disabled"})
            file-classes (map #(if (first %) (second %) "")
                           [[(not finished?) "file-incomplete"]
                            [disabled? "file-disabled"]])]
        (html
          [:div.file {:class (clojure.string/join " " file-classes)}
           (when editing
             [:span.file-edit
               {:on-click (fn [e] (om/transact! file :enabled not))}
               (if (:enabled file) "✔" "✖")])
           (if-not finished?
             [:span.file-name file_name]
             (html/link-to {:class "file-name"}
               (if (:multi_file? download)
                 (str "/file/" (:name download) "/" (:path file))
                 (str "/file/" (:path file)))
               file_name))

           (if-not finished?
             [:span.file-progress.badge disabled-tooltip (:progress file)]
             [:span.file-size.badge disabled-tooltip (common/filesize (:size file))])])))))

(defn folder-view [{[path {:keys [files folders]}] :folder
                    download :download
                    file-settings :file-settings
                    collapsed? :collapsed?
                    editing :editing
                    :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:collapsed collapsed?
       :enabled true})

    om/IWillReceiveProps
    (will-receive-props [this next-props]
      (when-not (= (:collapsed? (om/get-props owner))
                   (:collapsed? next-props))
        (om/set-state! owner :collapsed (:collapsed? next-props))))

    om/IRenderState
    (render-state [_ {:keys [collapsed root? enabled]}]
      (html
        [:div.folder
         [:div.folder-tab
          {:on-click #(om/update-state! owner :collapsed not)}
          (when editing
            [:span.file-edit
             {:on-click
              (fn [e]
                (.stopPropagation e)

                (let [toggle (not enabled)]
                  (doseq [file files]
                    (om/transact! file #(assoc-in @file [:enabled] toggle)))
                  (om/set-state! owner :enabled toggle)))}
             (if enabled "✔" "✖")])
          [:span.folder-name path]
          [:span.badge
           {:data-toggle "tooltip"
            :data-placement "top"
            :title "files in folder"}
           (str (count files))]]
         (when (or root? (not collapsed))
           [:div.entities
            (map #(om/build file-view {:file % :download download :editing editing}
                            {:react-key (get-in % [:id])})
                 (sort-by :name files))
            (map #(om/build folder-view
                            {:folder %
                             :download download
                             :file-settings file-settings
                             :collapsed? (:collapsed file-settings)
                             :editing editing}
                            {:react-key (first %)})
                 (sort #(common/compare-ignore-case (first %1) (first %2)) folders))])]))))

(defn detect-enabled-changes [original prs]
  (second
    (diff
      original
      (zipmap (mapv :id prs) (mapv :enabled prs)))))

(defn files-view [{:keys [files download file-settings editing] :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:chan (chan)
       :pattern ""
       :files-enabled
         (zipmap (mapv :id files) (mapv :enabled files))})

    om/IWillReceiveProps
    (will-receive-props [this next-props]
      (if (and (get-in (om/get-props owner) [:editing])
               (not (:editing next-props)))
        (let [diff (detect-enabled-changes
                      (om/get-state owner :files-enabled)
                      (:files next-props))]
          (common/api :post (str "/downloads/" (:hash download) "/files")
            {:priorities diff}
            (fn [m]
              (when (:success m)
                (om/set-state! owner :files-enabled diff)))))))

    om/IWillMount
    (will-mount [_]
      (let [chan (om/get-state owner :chan)]
        (go-loop []
          (let [[topic value :as msg] (<! chan)]
            (case topic
              :pattern (if (not= (om/get-state owner :pattern) value)
                         (om/set-state! owner [:pattern] value)))
            (recur)))))

    om/IRenderState
    (render-state [_ {:keys [pattern chan]}]
      (let [regex (common/fuzzy-search pattern)
            filtered (filter #(.test regex (:path %)) files)
            root (file-tree filtered)

            filtered-extracted (filter #(.test regex (:path %)) (:extracted-files download))
            extracted-files-root (file-tree filtered-extracted)
            file-count (+ (count filtered) (count filetered-extracted))]
        (html
          [:div.files-section
           (when (> file-count 1)
             (om/build file-search-view
               {:file-count file-count
                :file-settings file-settings
                :is-tree (or
                           (contains? (second root) :folders)
                           (contains? (second extracted-files-root) :folders))}
               {:init-state {:chan chan}}))

           [:div.files
            (om/build folder-view
              {:folder extracted-files-root
               :download download
               :file-settings file-settings
               :collapsed? (:collapsed file-settings)
               :editing false}
              {:init-state {:root? true}})]

           [:div.files
            (om/build folder-view
              {:folder root
               :download download
               :file-settings file-settings
               :collapsed? (:collapsed file-settings)
               :editing editing}
              {:init-state {:root? true}})]])))))

(defn- format-component [component pre label post]
  (when-not (= component 0)
    (str
      component
      pre
      (if (> component 1)
        (str label "s")
        label)
      post)))

(defn- eta [downloaded total down_rate]
  (let [remaining-bytes (- total downloaded)]
    (cond
      (= remaining-bytes 0) "done"
      (= down_rate 0) "not downloading"
      :else (let [remaining-seconds (/ remaining-bytes down_rate)
                  duration (.duration js/moment remaining-seconds "seconds")]
              (str
                (format-component (.years duration)   " " "year"   ", ")
                (format-component (.months duration)  " " "month"  ", ")
                (format-component (.days duration)    " " "day"    ", ")
                (format-component (.hours duration)   " " "hour"   ", ")
                (format-component (.minutes duration) " " "minute" ", ")
                (format-component (.seconds duration) " " "second" ""))))))

(defn download-page [{:keys [download found hash file-settings downloads current-user]
                      :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false
       :show-stats false
       :stats
        {:up_rate 0
         :down_rate 0
         :total_uploaded 0
         :eta ""
         :leechs 0
         :seeders 0}
       :websocket nil})

    ;; sub to ws feed
    om/IWillMount
    (will-mount [_]
      (if (and (empty? download) (not found))
        (common/bootstrap-or-get
          (str "/downloads/" hash)
          #(om/update! download %))

        (do
          (common/api :get (str "/downloads/" hash "/files")
            #(om/update! download [:files] %))

          (common/api :get (str "/downloads/" hash "/extracted")
            #(om/update! download [:extracted-files] %))))

      (om/set-state! owner :websocket
        (common/websocket (str "/downloads/" hash "/feed")))

      (set! (.-onmessage (om/get-state owner :websocket))
        (fn [msg]
          (let [json (.parse js/JSON (.-data msg))
                clj (js->clj json :keywordize-keys true)]
            (om/update! download clj)))))

    ;; unsub from ws feed
    om/IWillUnmount
    (will-unmount [_]
      (.close (om/get-state owner :websocket)))

    om/IRenderState
    (render-state [_ {:keys [editing show-stats]}]
      (let [{:keys [name hash state ratio message
                    progress uploader date-added
                    locks files total_uploaded
                    up_rate down_rate seeders leeches
                    completed_bytes size_bytes]} download
            username (:username current-user)]
        (html
          (common/spinner-when (empty? download)
            [:div
             [:div.download-header
              [:h4.download-name
               (clojure.string/replace (or name "") #"\." "\u200b.")]

              [:div.download-progress
               {:data-toggle "tooltip"
                :data-original-title state
                :data-placement "top"}
               [:div.download-progress-inner
                {:class (str "download-state-" state)
                 :style {:width (str progress "%")}
                 :aria-valuenow progress}]]

              [:div.download-meta
               [:div.download-added
                "added by " [:strong.download-uploader uploader] " on "
                [:time.download-date-added
                 {:title (format-time date-added :long)}
                 (format-time date-added :short)]
                (if (empty? locks)
                  [:span.download-expires-at
                   " and expires in "
                   (expires-format date-added)]
                  [:span.download-locks
                   " and locked by "
                   (interpose ", "
                     (map (partial conj [:strong]) locks))])]]]

             [:div.download-commands.btn-toolbar {:role "toolbar"}
              [:div.btn-group
                (let [locked ((set locks) username)]
                  (command-button {:class "lock-button"}
                    (if locked "unlock" "lock")
                    (if locked (common/glyphicon "link") (common/glyphicon "lock"))
                    (fn [e]
                      (common/api :post (str "/downloads/" hash "/lock-toggle")
                        {:username username}
                        (fn [m]
                          (om/update! download [:locks] m))))))

                (command-button {:class "btn-info"} "edit files" (common/glyphicon "check")
                  #(om/update-state! owner :editing not))

                (command-button {:class "btn-primary"} "stats" (common/glyphicon "stats")
                  #(om/update-state! owner :show-stats not))

                (if (or (= state "closed") (= state "stopped"))
                 (command-button {:class "btn-success"} "start" (common/glyphicon "play")
                   (fn [e]
                     (common/api :post (str "/downloads/" hash "/start")
                       {:start true}
                       (fn [m] (om/update! download m)))))

                 (command-button {:class "btn-warning"} "stop" (common/glyphicon "stop")
                   (fn [e]
                    (common/api :post (str "/downloads/" hash "/stop")
                      {:stop true}
                      (fn [m] (om/update! download m))))))

                (command-button {:class "btn-danger"} "erase" (common/glyphicon "remove")
                  (fn [e]
                    (if (or (common/admin? @current-user)
                            (and (= uploader username) (empty? @locks)))
                      (when (js/confirm "are you sure you want to delete this?")
                        (do
                          (.close (om/get-state owner :websocket))
                          (common/api :post (str "/downloads/" hash "/erase")
                            {:erase true}
                            (fn [m]
                              (om/update! downloads m)
                              (common/redirect "/downloads")))))
                      (js/alert
                        (cond
                          (not (empty? @locks)) (str "locked by " (clojure.string/join ", " @locks))
                          (not= uploader username) (str "only " uploader " can erase this")
                          :else "[ERROR] notify admin")))))]]

             (when show-stats
               [:div.download-stats
                (when (= state "downloading")
                  [:div.ratio [:strong "ETA: "]
                   (eta completed_bytes size_bytes down_rate)])
                [:div.ratio [:strong "Ratio: "] ratio]
                [:div.ratio [:strong "Total Uploaded: "]
                 (common/filesize total_uploaded)]
                [:div.ratio [:strong "Up Rate: "]
                 (str (common/filesize up_rate ) "/s")]
                [:div.ratio [:strong "Down Rate: "]
                 (str (common/filesize down_rate) "/s")]
                [:div.ratio [:strong "seeders: "] seeders]
                [:div.ratio [:strong "leeches "] leeches]
                ])

             (when-not (clojure.string/blank? message)
               [:div.alert.alert-warning.download-message {:role "alert"} message])

             (common/spinner-when (nil? files)
               (om/build files-view
                 {:files files
                  :download download
                  :file-settings file-settings
                  :editing editing}))]))))))

