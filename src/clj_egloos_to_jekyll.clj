(ns clj-egloos-to-jekyll
  (:gen-class))

(require '[org.httpkit.client :as http])
(require '[clojure.java.io :as io])
(require '[net.cgrand.enlive-html :as html])

(def total-page 103)

(def target-egloos-url "http://toracle.egloos.com")

(def articles-url-data-path "data/article-urls.dat")

(def content-list-dir "data/content-list/")

(def articles-dir "data/article/")

(def articles-output-dir "data/_posts/")


;; Get Content List

(defn content-list-path [filename]
  (str content-list-dir filename))

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
      (save-webpage-to-file url (content-list-path (format "page-%s.html"
                                                           (get-page-num-from-url url)))))))

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
                          (content-list-path (format "page-%s.html" page-num)))
                        (range 1 (+ total-page 1)))]
    (flatten
     (map (fn [filename]
            (get-article-urls-from-content-list-page-file filename))
          data-files))))

(defn save-article-urls []
  (let [article-urls (get-article-urls)]
    (spit articles-url-data-path (prn-str article-urls))))

(defn save-all-article []
  (let
      [article-urls (read-string (slurp articles-url-data-path))]
    (map (fn [url]
           (save-webpage-to-file url (format "%sarticle-%s.html"
                                             articles-dir
                                             (get-page-num-from-url url))))
         article-urls)))

(defn convert-dom-to-markdown [root]
  (let
      [content (map (fn [element]
                      (let [tag (:tag element)]
                        (cond (string? element) element
                              (= tag :br) "\n"
                              (= tag :b) (format "**%s**"
                                                 (html/text element))
                              (= tag :a) (if
                                             (nil? (:content element))
                                           ""
                                           (format "[%s](%s)"
                                                   (convert-dom-to-markdown (:content element))
                                                   (:href (:attrs element))))
                              (= tag :img) (do
                                             (format "[%s]"
                                                     (:src (:attrs element))))
                              (= tag :div) (convert-dom-to-markdown (:content element))
                              (= tag :blockquote) (format "> %s"
                                                          (convert-dom-to-markdown (:content element)))
                              (= tag :font) (convert-dom-to-markdown (:content element))
                              (= tag :span) (convert-dom-to-markdown (:content element))
                              (= tag :p) (format "\n\n%s\n\n"
                                                 (convert-dom-to-markdown (:content element)))
                              (= tag :hr) "***"
                              :else (clojure.string/trim (html/text element)))))
                    root)]
    (->> content
         flatten
         (remove nil?)
         (clojure.string/join ""))))

(defn slugify [s]
  (-> s
      (clojure.string/replace " " "-")
      (clojure.string/replace "," "")
      (clojure.string/replace "[" "")
      (clojure.string/replace "]" "")
      (clojure.string/replace "?" "")
      (clojure.string/replace ":" "-")
      (clojure.string/replace "." "")))

(defn parse-article-content [content]
  {:title (-> content
              java.io.StringReader.
              html/html-resource
              (html/select [:h2.entry-title :a])
              first
              html/text)
   :title-slug (-> content
              java.io.StringReader.
              html/html-resource
              (html/select [:h2.entry-title :a])
              first
              html/text
              slugify)
   :datetime (-> content
             java.io.StringReader.
             html/html-resource
             (html/select [:li.post_info_date :abbr])
             first
             html/text
             (clojure.string/replace "/" "-"))
   :date (-> content
             java.io.StringReader.
             html/html-resource
             (html/select [:li.post_info_date :abbr])
             first
             html/text
             (clojure.string/replace "/" "-")
             (subs 0 10))
   :content (-> content
                java.io.StringReader.
                html/html-resource
                (html/select [:div.post_content :div.hentry])
                first
                :content
                convert-dom-to-markdown
                )
   :category (-> content
                 java.io.StringReader.
                 html/html-resource
                 (html/select [:span.post_title_category :a])
                 first
                 html/text)})

(defn convert-parsed-to-markdown-article [m]
  (format "---\nlayout: post\ntitle: \"%s\"\ndate: %s\ncategories: %s\n---\n\n%s"
          (:title m) (:datetime m) (:category m) (:content m)))

(defn convert-all-article-to-markdown []
  (let [article-file-list (rest (file-seq (java.io.File. articles-dir)))]
    (map (fn [file]
           (let [parsed (parse-article-content (slurp file))
                 body (convert-parsed-to-markdown-article parsed)
                 out-file (java.io.File. (format "%s%s-%s.md"
                                                 articles-output-dir
                                                 (:date parsed)
                                                 (:title-slug parsed)))]
             (.mkdirs (.getParentFile out-file))
             (with-open [wrtr (io/writer out-file)]
               (.write wrtr body))))
          article-file-list)))

(defn -main []
  (do
    (save-all-content-list)
    (save-article-urls)
    (save-all-article)
    (convert-all-article-to-markdown)))
