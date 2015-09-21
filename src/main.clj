(ns clj-egloos-dump)

(require '[org.httpkit.client :as http])
(require '[clojure.java.io :as io])
(require '[net.cgrand.enlive-html :as html])

(def total-page 103)

(def target-egloos-url "http://toracle.egloos.com")

;; Get Content List

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

(defn get-page-num-from-url [url]
  (.substring url (+ 1 (.lastIndexOf url "/"))))

(defn save-all-content-list []
  (let
      [content-list-urls (map (fn [page-num]
                                (target-egloos-page-url page-num))
                              (range 1 (+ total-page 1)))]
    (doseq [url content-list-urls]
      (save-webpage-to-file url (content-list-path (str "page-" (get-page-num-from-url url) ".html"))))))

;; Get Article List

(defn get-article-urls-from-content-list-page [content]
  (map
   (fn [element] (str "http://" (html/text element)))
   (html/select (html/html-resource (java.io.StringReader. content)) [:li.post_info_link :a])))

(defn get-article-urls-from-content-list-page-file [filename]
  (let [file (java.io.File. filename)
        content (slurp file)]
    (get-article-urls-from-content-list-page content)))

(defn get-article-urls []
  (let [data-files (map (fn [page-num]
                          (content-list-path (str "page-" page-num ".html")))
                        (range 1 (+ total-page 1)))]
    (flatten
     (map (fn [filename]
            (get-article-urls-from-content-list-page-file filename))
          data-files))))

(defn save-article-urls []
  (let [article-urls (get-article-urls)]
    (spit "data/article-urls.dat" (prn-str article-urls))))

(defn save-all-article []
  (let
      [article-urls (read-string(slurp "data/article-urls.dat"))]
    (map (fn [url]
           (save-webpage-to-file url (str "data/article/article-" (get-page-num-from-url url) ".html")))
         article-urls))
  true)

(defn convert-to-markdown-dom [root]
  (let
      [content (map (fn [element]
                      (cond (string? element) element
                            (= (:tag element) :br) "\n"
                            (= (:tag element) :a) (format "[%s](%s)" (:href (:attrs element)) (html/text element))
                            :else nil))
                    root)]
    (->> content
         flatten
         (remove nil?)
         pr-str
         (clojure.string/join ""))))

(defn parse-article-content [content]
  {:title (-> content
              java.io.StringReader.
              html/html-resource
              (html/select [:h2.entry-title :a])
              first
              html/text),
   :date (-> content
             java.io.StringReader.
             html/html-resource
             (html/select [:li.post_info_date :abbr])
             first
             html/text)
   :content (-> content
                java.io.StringReader.
                html/html-resource
                (html/select [:div.post_content :div.hentry])
                first
                :content
                )})
