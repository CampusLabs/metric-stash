(ns metric-stash
  (:refer-clojure :exclude [time])
  (:require [clojure.core.async
             :as async
             :refer [chan go-loop put! alt! timeout close! sliding-buffer]]
            [clojure.java.io :refer [writer]]
            [clj-time.core :as t]
            [cheshire.core :refer [generate-string]]
            [com.stuartsierra.component :as component])
  (:import [java.io BufferedWriter Closeable]))

(defprotocol MetricWriter
  (mark
    [metrics service]
    [metrics service options])
  (time
    [metrics service value]
    [metrics service value options])
  (gauge
    [metrics service value]
    [metrics service value options]))

(defrecord NullMetricWriter []
  component/Lifecycle
  (start [metrics])
  (stop [metrics])

  MetricWriter
  (mark [metrics service])
  (mark [metrics service options])
  (time [metrics service value])
  (time [metrics service value options])
  (gauge [metrics service value])
  (gauge [metrics service value options]))

(defn null-metric-writer []
  (NullMetricWriter.))

(defn- now [] (str (t/now)))

(defn- construct-metric
  [app-name service metric metric-type timestamp {:keys [tags]
                                                  :or {tags #{}}
                                                  :as options}]
  (let [metric {:app_name app-name
                :service service
                :metric metric
                :tags (into #{metric-type} tags)
                "@timestamp" timestamp}
        options (select-keys options [:state :description])]
    (merge metric options)))

(defrecord JsonFileWriter [app-name path buffer-length flush-interval channel]
  component/Lifecycle
  (start [metrics]
    (let [channel (chan (sliding-buffer buffer-length))
          file-stream (writer path :append true)]
      (go-loop []
        (if (alt!
              channel
              ([v] (when v
                     (.write ^BufferedWriter file-stream
                             (str (generate-string v) \newline))
                     true))

              (timeout flush-interval)
              ([_] (do (.flush ^BufferedWriter file-stream)
                       true)))
          (recur)
          (.close ^BufferedWriter file-stream)))
      (assoc metrics :channel channel)))

  (stop [metrics]
    (close! channel)
    (assoc metrics :channel nil))

  MetricWriter
  (mark [metrics service]
    (mark metrics service {}))

  (mark [_ service options]
    (put! channel (construct-metric app-name service 1 :mark (now) options)))

  (time [metrics service value]
    (time metrics service value {}))

  (time [_ service value options]
    (put! channel (construct-metric app-name service value :time (now) options)))

  (gauge [metrics service value]
    (gauge metrics service value {}))

  (gauge [_ service value options]
    (put! channel (construct-metric app-name service value :gauge (now) options)))

  Closeable
  (close [_] (close! channel)))

(defn json-file-metric-writer
  ([app-name path] (json-file-metric-writer app-name path {}))
  ([app-name path {:keys [buffer-length flush-interval]
                    :or {buffer-length 1024
                         flush-interval 1000}}]

   (JsonFileWriter. app-name path buffer-length flush-interval nil)))

(defmacro benchmark [metrics service & body]
  "Benchmark the execution of body, writing the time in nanoseconds to
  a metric for the supplied service string. Returns the result of
  executing body."
  `(let [start# (System/nanoTime)
         result# (do ~@body)
         end# (System/nanoTime)]
     (time ~metrics ~service (- end# start#))
     result#))

(defmacro meter [metrics service & body]
  "Record an event metric for the supplied service string. Returns the
  result of executing body."
  `(let [result# (do ~@body)]
     (mark ~metrics ~service)
     result#))

(defmacro measure [metrics service & body]
  "Record the result of executing body as a metric with the supplied
  service string. Returns the result of executing body."
  `(let [result# (do ~@body)]
     (gauge ~metrics ~service result#)
     result#))
