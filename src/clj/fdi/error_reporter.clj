(ns fdi.error-reporter
  (:require [clojure.core.async :as async :refer [<! go]]))

(defn start [source-channel error-callback]
  (go
   (loop [error (<! source-channel)]
     (when-not (identical? error :stop)
       (error-callback error)
       (recur (<! source-channel))))))
