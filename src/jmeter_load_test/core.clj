(ns jmeter-load-test.core
  (:require [jmeter-load-test.init] ;; need to set some jmeter config before importing the below modules
            [clojure.java.io :as io])
  (:import [org.apache.jmeter.control LoopController]
           [org.apache.jmeter.engine StandardJMeterEngine]
           [org.apache.jmeter.protocol.http.sampler HTTPSamplerProxy]
           [org.apache.jmeter.reporters ResultCollector Summariser]
           [org.apache.jmeter.testelement TestPlan]
           [org.apache.jorphan.collections HashTree]))

;; See https://bitbucket.org/blazemeter/jmeter-from-code/src/57cef9e2b1c9?at=master

(defn build-sampler [domain path]
  (doto (HTTPSamplerProxy.)
    (.setDomain domain)
    (.setPort 80)
    (.setPath path)
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
      (.setRampUp 1)
      (.setSamplerController loop-controller))))

(defn -main [domain n threads]
  (let [jmeter-home (.getPath (io/resource "jmeter"))
        jmeter (StandardJMeterEngine.)
        test-plan-tree (HashTree.)
        test-plan (TestPlan.)
        my-summariser (proxy [Summariser] []
                        (sampleOccurred [e]
                          (let [res (.getResult e)]
                            (println {:response-code (.getResponseCode res)
                                      :start-time (.getStartTime res)
                                      :end-time (.getEndTime res)
                                      :timestamp (.getTimeStamp res)
                                      :response-time (.getTime res)
                                      :url (.getURL res)
                                      :success? (.isSuccessful res)})))
                        (testStarted [_])
                        (testEnded [_]))
        logger (ResultCollector. my-summariser)]

    ;; Construct Test Plan from previously initialized elements
    (.add test-plan-tree test-plan)
    (doto (.add test-plan-tree test-plan (build-thread-group n threads))
      (.add (build-sampler "gocardless.com" "/")))

    ;; Store execution results into a .jtl file
    ; (.setFilename logger (str jmeter-home "/example.jtl"))
    (.add test-plan-tree (aget (.getArray test-plan-tree) 0) logger)

    ;; Run Test Plan
    (.configure jmeter test-plan-tree)
    (.run jmeter)
    (println "DONE")))

(comment (-main "gocardless.com" 10 5))
