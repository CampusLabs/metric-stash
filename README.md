# metric-stash

A simple library to emit metrics to a log file suitable for
consumption by [logstash][logstash]. JSON-formatted metrics are
written asynchronously and periodically flushed to disk.

## Usage

#### JSON file metric writer

Create a metric writer with your application name and the path to the
emitted log file:

```clj
(require '[metric-stash :as m])
(def metrics (m/json-file-metric-writer "my-application" "logs/metrics.log"))
```

An additional options map can be provided that supports the following
keys:

| Key              | Type    | Default | Description                     |
|------------------|---------|---------|---------------------------------|
|`:buffer-length`  | Integer | `1024`  | metric buffer length (messages) |
|`:flush-interval` | Integer | `1000`  | file flush interval (ms)        |

Metrics will be asynchronously written to a file stream from a queue
of length `:buffer-length` and flushed to disk every `:flush-interval`
milliseconds. Rather than pausing the application or consuming an
unbounded amount of memory, older metrics will be dropped if the
buffer is full.

`json-file-metric-writer` returns a record that implements the
[component][component] `Lifecycle` protocol. To initialize the metrics
writer, call `start` (and call `stop` to shutdown the writer and close
the file):

```clj
(require '[com.stuartsierra.component :as component])
(alter-var-root #'metrics component/start)
```

#### Metric types

Three types of metrics are supported:

| Type   | Description       | Usage                          |
|--------|-------------------|--------------------------------|
|`mark`  | record an event   | `(mark metrics "service")`     |
|`time`  | record a duration | `(time metrics "service" 100)` |
|`gauge` | record a value    | `(gauge metrics "service" 42)` |

`metrics` is the metric writer and `service` is a string representing
the name of the specific metric being recorded. For example:

```clj
(m/gauge metrics "workers" 42)
```

will emit the following log line to `"logs/metrics.log"`:

```json
{"app_name":"my-application","service":"workers","metric":42,"tags":["gauge"],"@timestamp":"2015-09-03T16:56:45.652Z"}
```

#### Additional metric options

Each metric type also supports an options map for supplying additional
information about the metric:

| Option Key     | Description                                                |
|----------------|------------------------------------------------------------|
| `:tags`        | a set of event tag strings                                 |
| `:state`       | typically something like `"ok"`, `"warning"`, or `"fatal"` |
| `:description` | a description of the event (could be used for alerting)    |
| `:@timestamp`  | override the event timestamp (ISO 8601 string)             |

For example:

```clj
(m/mark metrics "server.start"
        {:state "ok"
         :tags #{"operation"}
         :description "server has successfully started"})
```

This will emit a log line like the following:

```json
{"app_name":"my-application","service":"server.start","metric":1,"tags":["mark","operation"],"@timestamp":"2015-09-03T20:19:42.229Z","state":"ok","description":"server has successfully started"}
```

#### Macro interface

A set of convenience macros are provided to make writing metrics less
intrusive:

| Name        | Metric  | Usage                                | Records                  |
|-------------|---------|--------------------------------------|--------------------------|
| `benchmark` | `time`  | `(benchmark metrics service & body)` | body execution time (ns) |
| `meter`     | `mark`  | `(meter metrics service & body)`     | event                    |
| `measure`   | `gauge` | `(measure metrics service & body)`   | body result              |

Each macro executes the body, records the associated metric, and
returns the body result. For example:

```clj
(m/benchmark metrics "sleep.interval" (do (Thread/sleep 1000) 42)) ;;=> 42
```

```json
{"app_name":"my-application","service":"sleep.interval","metric":1000679338,"tags":["time"],"@timestamp":"2015-09-03T20:40:55.337Z"}
```

#### Null metric writer

A null metric writer is also included for testing:

```clj
(def null-metrics (m/null-metric-writer))
(m/mark null-metrics "server.start")
```

## License

Copyright © 2015 [Orgsync][orgsync].

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[logstash]: https://www.elastic.co/products/logstash
[component]: https://github.com/stuartsierra/component
[orgsync]: http://www.orgsync.com
