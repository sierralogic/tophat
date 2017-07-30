(defproject tophat "0.1.2"
  :description "Tophat is a Clojure library for handling HTTP request and response documents/maps."
  :url "http://sierralogic.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.5.0"] ; json handling
                 [com.cognitect/transit-clj "0.8.300"]
                 [io.forward/yaml "1.0.5"]]
  :plugins [[lein-codox "0.10.3"]])