(ns clj-egloos-dump)

(require '[org.httpkit.client :as http])
(require '[clojure.java.io :as io])

(def total-page 103)

(def target-egloos-url "http://toracle.egloos.com")

(defn content-list-path [filename]
  (str "data/content-list/" filename))

(defn target-egloos-page-url [page-num]
  (str target-egloos-url "/page/" page-num))

(defn save-webpage-to-file [url filename]
  (let [response (http/get url)
        body (:body @response)
        file (java.io.File. filename)]
    (.mkdirs (.getParentFile file))
    (with-open [wrtr (io/writer file)]
      (.write wrtr body))
    true))

(defn save-all-content-list []
  (map [(fn [page-num] (target-egloos-page-url page-num)) (range 1 (+ total-page 1))]))

(save-all-content-list)

(defn get-article-links-from-content [content]
  )
