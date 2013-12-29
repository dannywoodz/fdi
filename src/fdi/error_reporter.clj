(ns fdi.error-reporter
  (:require [clojure.core.async :as async :refer :all]))

(defn start [source-channel error-callback]
  (go
   (println "Starting error reporter")
   (loop [error (<! source-channel)]
     (println "Error reporter handling" error)
     (when-not (identical? error :stop)
       (error-callback error)
       (recur (<! source-channel))))))