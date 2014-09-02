(ns levee.test.rtorrent
  (:require [clojure.test :refer :all]
            [levee.rtorrent :as rtorrent]))

;; TODO: use torrent fixtures
(def test-hash "")

(deftest get-name
  (testing "retrieve torrent name"
    (let [name (:name (rtorrent/torrent test-hash :name))]
      (is (not (clojure.string/blank? name))))))

(deftest prettify-calls
  (testing "prettified calls"
    (is (= (#'rtorrent/prettify-calls
            [:is_complete
             :get_name
             :up.rate
             :get_custom=metadata])
           '(:complete?
             :name
             :up-rate
             :metadata)))))

