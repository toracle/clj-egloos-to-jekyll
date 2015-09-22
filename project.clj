(defproject clj-egloos-to-jekyll "1.0.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.18"]
                 [enlive "1.1.6"]]
  :profiles {:dev {:plugins [[lein-midje "3.1.1"]]}})
