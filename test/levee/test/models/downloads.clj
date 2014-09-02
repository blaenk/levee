(ns levee.test.models.downloads
  (:require [clojure.test :refer :all]
            [levee.models.downloads :as downloads]))

(deftest torrent-state
  (testing "correct status determination"
    (are [state        hashing open  active complete]
         (= state (#'downloads/get-state
                       hashing open  active complete))
         "closed"      false   false false  false
         "hashing"     true    false false  false
         "stopped"     false   true  false  false
         "downloading" false   true  true   false
         "seeding"     false   true  true   true)))

