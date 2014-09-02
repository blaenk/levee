(ns levee.client.core)

(defmacro app-link [opts to text]
  `(let [opts# ~opts
         to# ~to
         text# ~text]
     [:a
       (assoc opts#
              :href to#
              :on-click
              (fn [e#]
                (when (not= 1 (.-button e#))
                  (.setToken history to#)
                  (.preventDefault e#))))
       text#]))

