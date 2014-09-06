(ns levee.client.components.upload
  (:require
    [cljs.core.async :refer [put! chan <! mult tap close! untap]]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    [sablono.core :as html :refer-macros [html defelem defhtml]]
    [goog.events :as events]
    [goog.history.EventType :as EventType]
    [levee.client.common :as common]
    [clojure.data :refer [diff]]
    [jayq.core :refer [$ on off ajax]])
  (:require-macros
    [cljs.core.async.macros :refer [go-loop]]))

(defn- stop-event [e]
  (doto e
    (.stopPropagation)
    (.preventDefault)))

(defn file-upload [{:keys [file search]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:uploading false
       :upload-progress 0})

    om/IWillMount
    (will-mount [_]
      (let [upload-mult (om/get-state owner :upload-mult)]
        (om/set-state! owner :upload-chan
                       (let [c (chan)]
                         (tap upload-mult c)
                         c)))

      (let [upload-chan (om/get-state owner :upload-chan)]
        (om/set-state! owner :go-loop (go-loop []
                  (let [upload (<! upload-chan)]
                    (.click ($ (om/get-node owner "upload-button")))
                    (recur))))))

    om/IWillUnmount
    (will-unmount [_]
      (untap (om/get-state owner :upload-mult)
             (om/get-state owner :upload-chan)))

    om/IRenderState
    (render-state [_ {:keys [remove-chan uploading upload-progress]}]
      (html
        [:li.file-upload
         [:span.file-size.badge (common/filesize (.-size file))]
         [:span.file-name (.-name file)]
         (if uploading
           [:div.upload-progress.badge upload-progress]
           [:div.upload-controls
            [:div.checkbox
             {:data-toggle "tooltip"
              :data-placement "top"
              :title "don't start it if you need to select which files to download"}
             [:label
              [:input
               {:type "checkbox"
                :default-checked true
                :ref "start-checkbox"}]
              "start"]]
            [:button.btn.btn-xs.btn-success
             {:type "button"
              :ref "upload-button"
              :on-click
               (fn [e]
                 (om/set-state! owner :uploading true)
                 (let [formdata (js/FormData.)
                       start? (.-checked (om/get-node owner "start-checkbox"))]
                   (.append formdata "file" file)
                   (.append formdata "start" start?)

                   (common/api :post "/downloads" formdata
                    {:xhr
                     (fn []
                       (let [xhr (js/XMLHttpRequest.)]
                         (.addEventListener (.-upload xhr) "progress"
                           (fn [e]
                             (let [progress (if (.-lengthComputable e)
                                              (/ (* (.-loaded e) 100) (.-total e))
                                              0)]
                               (om/set-state! owner :upload-progress progress)))
                           false)
                         xhr))
                     :processData false
                     :contentType false
                     :success
                      (fn [d]
                        (om/set-state! owner :uploading false)
                        (put! remove-chan [:remove file])
                        (om/update! search [:scope] "mine")
                        (om/update! search [:sort] "recent")
                        (om/update! search [:pattern] ""))})))}
             "upload"]
            [:button.btn.btn-xs.btn-danger
             {:type "button"
              :on-click
               (fn [e] (put! remove-chan [:remove file]))}
             "remove"]])]))))

(defn- valid-torrent [file]
  (or
    (= (.-type file) "application/x-bittorrent")
    (re-find #"\.torrent$" (.-name file))))

(defn upload-component [{:keys [uploading search] :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [upload-chan (chan)
            upload-mult (mult upload-chan)]
        {:files []
        :dragging false
        :uploading uploading
        :remove-chan (chan)
        :upload-chan upload-chan
        :upload-mult upload-mult}))

    om/IWillMount
    (will-mount [_]
      (let [remove-chan (om/get-state owner :remove-chan)]
        (go-loop []
          (let [[topic value :as msg] (<! remove-chan)]
            (case topic
              :remove (om/update-state! owner :files
                        (fn [fs] (vec (remove #(= value %) fs))))
              nil)
            (recur))))

      (off ($ js/document) ".upload")

      (on ($ js/document) "dragover.upload" nil nil stop-event)

      (on ($ js/document) "dragenter.upload" nil nil
        (fn [e]
          (stop-event e)
          (.show ($ ".upload-overlay"))
          (om/set-state! owner :dragging true)
          (put! (om/get-state owner [:app-chan]) [:dragging true])))

      (on ($ ".upload-overlay") "dragleave.upload" nil nil
        (fn [e]
          (stop-event e)
          (.hide ($ ".upload-overlay"))
          (om/set-state! owner :dragging false)
          (put! (om/get-state owner [:app-chan]) [:dragging false])))

      (on ($ ".upload-overlay") "drop.upload" nil nil
        (fn [e]
          (stop-event e)
          (.hide ($ ".upload-overlay"))
          (om/set-state! owner :dragging false)
          (put! (om/get-state owner [:app-chan]) [:dragging false])
          (let [dt (.. e -originalEvent -dataTransfer)
                files (array-seq (.-files dt))
                filtered (filter valid-torrent files)]
            (om/update-state! owner :files #(concat % filtered))
            (om/transact! props #(assoc % :uploading true))))))

    om/IWillUnmount
    (will-unmount [_]
      (off ($ js/document) ".upload"))

    om/IRenderState
    (render-state [_ {:keys [app-chan remove-chan upload-chan upload-mult files dragging]}]
      (html
        (when uploading
          [:div.upload
           [:div.row.magnet
            [:div.col-lg-6.search
             [:div.input-group
              [:input.form-control
               {:type "text"
                :auto-focus "off"
                :placeholder "Magnet URI"
                :auto-complete "off"
                :ref "magnet-box"
                :on-change
                 (fn [e])}]
              [:span.input-group-addon
               [:div.checkbox
                [:label
                  [:input
                   {:type "checkbox"
                    :ref "magnet-start"
                    :default-checked true}]
                 "start"]]
               ]
              [:span.input-group-btn
               [:button.btn.btn-info.send-magnet
                {:type "button"
                 :on-click
                  (fn [e]
                    (common/api :post "/downloads/magnet"
                      {:uri (.-value (om/get-node owner "magnet-box"))
                       :start (.-checked (om/get-node owner "magnet-start"))}
                      (fn [d]
                        (set! (.-value (om/get-node owner "magnet-box")) "")
                        (om/update! search [:scope] "mine")
                        (om/update! search [:sort] "recent")
                        (om/update! search [:pattern] "")
                        )))}
                "submit"]]
              ]]]
           [:div.upload-buttons
            [:input
             {:type "file"
              :multiple true
              :style {:display "none"}
              :ref "file-input"
              :on-change
               (fn [e]
                 (let [files (array-seq (.. e -target -files))
                       filtered (filter valid-torrent files)]
                   (om/update-state! owner :files #(concat % filtered))
                   ))}]
            [:button.btn.btn-primary.upload-file
             {:type "button"
              :on-click
               (fn [e]
                 (.preventDefault e)
                 (.click ($ (om/get-node owner "file-input"))))}
             "files"]
            [:button.btn.btn-success.upload-all
             {:type "button"
              :disabled (empty? files)
              :on-click
               (fn [e]
                 (.preventDefault e)
                 (put! upload-chan "upload"))
              }
             "upload all"]]
           [:ul.uploads
            (map #(om/build file-upload
                   {:file %
                    :search (:search props)}
                   {:init-state
                    {:remove-chan remove-chan
                     :upload-mult upload-mult}})
                 files)]])))))

