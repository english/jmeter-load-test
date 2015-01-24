(ns jmeter-load-test.init
  (:require [clojure.java.io :as io])
  (:import [org.apache.jmeter.util JMeterUtils]))

(let [jmeter-home (.getPath (io/resource "jmeter"))]
  (JMeterUtils/setJMeterHome jmeter-home)
  (JMeterUtils/loadJMeterProperties (str jmeter-home "/jmeter.properties"))
  (JMeterUtils/initLogging)) ;; sets logging level to INFO, without this it will be DEBUG
