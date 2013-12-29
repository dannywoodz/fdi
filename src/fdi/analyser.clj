(ns fdi.analyser
  (:require [clojure.core.async :as async :refer :all]))

(defn start [fingerprint-channel duplicate-handler]
  (go
   (println "Starting analyser")
   (loop [fingerprint (<! fingerprint-channel)]
     (println "Analysing" fingerprint)
     (when-let [{:keys [fingerprint filename] :as print} fingerprint]
       (duplicate-handler print)
       (recur (<! fingerprint-channel))))))
