(defproject levee "0.1.0"
  :description "rtorrent interface"
  :url "https://github.com/blaenk/levee"
  :dependencies
    [[org.clojure/clojure "1.6.0"]
     [org.clojure/core.async "0.1.303.0-886421-alpha"]

     [http-kit "2.1.19"]
     [ring "1.3.1"]
     [ring/ring-json "0.3.1"]
     [ring-accept-param "0.1.1"]
     [bk/ring-gzip "0.1.1"]
     [ring.middleware.logger "0.5.0"]
     [prone "0.6.0"]
     [compojure "1.1.8"]
     [hiccup "1.0.5"]

     [korma "0.4.0"]
     [org.xerial/sqlite-jdbc "3.7.15-M1"]

     [cheshire "5.3.1"]
     [bencode "0.2.5"]
     [org.clojure/data.codec "0.1.0"]

     [org.apache.commons/commons-io "1.3.2"]
     [environ "1.0.0"]
     [com.taoensso/timbre "3.2.1"]
     [com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache]]

     [me.raynes/fs "1.4.6"]

     ;; necessary-evil hasn't been updated in a while
     ;; and it has very old dependency constraints
     [necessary-evil "2.0.0" :exclusions [clj-time commons-codec]]

     ;; cljs
     [org.clojure/clojurescript "0.0-2322"]
     [secretary "1.2.1-20140627.190529-1"]
     [om "0.7.1"]
     [sablono "0.2.22"]
     [prismatic/dommy "0.1.3"]
     [cljs-ajax "0.2.6"]
     [jayq "2.5.2"]]
  :source-paths ["src/clj"]
  :main levee.main
  :jvm-opts ["-Xmx1g"]
  :profiles
    {:default [:base :system :user :provided :dev :local-dev]
     :prod
      {:env
       ;; best to use strings here since env-vars and java props
       ;; will themselves be strings, keeps it consistent
       {:env "prod"
        :port "3000"
        :rtorrent "localhost:5000"
        :db "levee.db"}}
     :dev
      {:source-paths ["dev"]
       :aliases
         {"clean" ^{:doc "also clean cljs"}
          ["do"
           "clean"
           ["cljsbuild" "clean"]]

          "less" ^{:doc "watch less with grunt"}
          ["do"
           ["shell" "grunt" "watch"]]

          "build" ^{:doc "build levee"}
          ["do"
            "clean"
            ["shell" "grunt" "less"]
            ["cljsbuild" "once" "prod"]
            ["uberjar"]]}
       :plugins
         [[com.cemerick/austin "0.1.5"]
          [lein-environ "1.0.0"]
          [lein-cljsbuild "1.0.3"]
          [lein-figwheel "0.1.4-SNAPSHOT"]
          [lein-shell "0.4.0"]
          [lein-ancient "0.5.5"]]
       ;; :hooks [leiningen.cljsbuild]
       :dependencies
         [[org.clojure/tools.namespace "0.2.5"]
          [org.clojure/tools.trace "0.7.8"]
          [org.clojure/tools.nrepl "0.2.4"]
          [cider/cider-nrepl "0.7.0"]
          [figwheel "0.1.4-SNAPSHOT"]
          [com.cemerick/piggieback "0.1.3"]
          [im.chit/vinyasa "0.2.2"
           :exclusions [org.codehaus.plexus/plexus-utils]]]
      :injections
        [(require '[vinyasa.inject :as inject])
         (inject/in
           [vinyasa.pull :all]

           clojure.core >
           [clojure.tools.namespace.repl refresh]
           [clojure.pprint pprint pp]
           [clojure.stacktrace print-stack-trace])]
      :figwheel
        {:css-dirs ["resources/public/css"]}
      }
     :uberjar
      {:omit-source true
       ; only use this here, not in dev else stuff breaks
       :aot :all}}
  :cljsbuild
   {:builds
    {:dev
      {:source-paths ["src/cljs" "src/extras/figwheel"]
       :compiler
        {:output-to "resources/public/js/main.js"
         :output-dir "resources/public/js/cljs/"
         :optimizations :none
         :source-map true}}
     :prod
      {:source-paths ["src/cljs"]
       :compiler
        {:output-to "resources/public/js/main.js"
         :optimizations :advanced
         :pretty-print false
         :externs
         ["src/extras/externs/externs.js"
          "src/extras/externs/jquery-1.9.js"
          "react/externs/react.js"]}}}})

