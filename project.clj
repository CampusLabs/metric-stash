(defproject orgsync/metric-stash "0.1.0"
  :description "Write application metrics to a JSON-formatted file log"
  :url "https://github.com/orgsync/metric-stash"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.stuartsierra/component "0.2.3"]
                 [clj-time "0.11.0"]
                 [cheshire "5.5.0"]])
