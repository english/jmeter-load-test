(ns jmeter-load-test.core
  (:require [jmeter-load-test.init] ;; need to set some jmeter config before importing the below modules
            [clojure.java.io :as io]
            [clojure.core.async :as async])
  (:import [org.apache.jmeter.control LoopController]
           [org.apache.jmeter.engine StandardJMeterEngine]
           [org.apache.jmeter.protocol.http.sampler HTTPSamplerProxy]
           [org.apache.jmeter.reporters ResultCollector Summariser]
           [org.apache.jmeter.testelement TestPlan]
           [org.apache.jorphan.collections HashTree]))

;; See https://bitbucket.org/blazemeter/jmeter-from-code/src/57cef9e2b1c9?at=master

(defn build-sampler [url]
  (doto (HTTPSamplerProxy.)
    (.setPath url)
    (.setMethod "GET")))

(defn build-loop-controller [n]
  (doto (LoopController.)
    (.setLoops n)
    (.setFirst true)
    (.initialize)))

(defn build-thread-group [n c]
  (let [loop-controller (build-loop-controller n)]
    (doto (org.apache.jmeter.threads.ThreadGroup.)
      (.setNumThreads c)
      (.setSamplerController loop-controller))))

(defn build-logger [chan]
  (ResultCollector. (proxy [Summariser] []
                      (sampleOccurred [e]
                        (async/go (async/>! chan (.getResult e))))
                      (testStarted [_])
                      (testEnded [_]))))

(defn result->map [result]
  {:response-code (.getResponseCode result)
   :start-time (.getStartTime result)
   :end-time (.getEndTime result)
   :timestamp (.getTimeStamp result)
   :response-time (.getTime result)
   :url (.getURL result)
   :success? (.isSuccessful result)})

(defn blast! [url n concurrency c]
  (let [jmeter-home (.getPath (io/resource "jmeter"))
        jmeter (StandardJMeterEngine.)
        test-plan-tree (HashTree.)
        test-plan (TestPlan.)
        results-chan (async/chan 1 (map result->map))
        logger (build-logger results-chan)]

    (async/pipe results-chan c)

    ;; Construct Test Plan from previously initialized elements
    (.add test-plan-tree test-plan)
    (doto (.add test-plan-tree test-plan (build-thread-group n concurrency))
      (.add (build-sampler url)))

    (.add test-plan-tree (aget (.getArray test-plan-tree) 0) logger)

    ;; Run Test Plan
    (.configure jmeter test-plan-tree)
    (.run jmeter)
    nil))

(defn -main [url n concurrency]
  (let [results-chan (async/chan (async/dropping-buffer 1) (map println))]
    (blast! url n concurrency results-chan)))

(comment (-main "https://gocardless.com/about" 1 5))
