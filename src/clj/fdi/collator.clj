(ns fdi.collator
  (:require [clojure.core.async :as async :refer [go >! <!]]))


(defn start [fingerprint-channel analyser-channel]
  (go
   (loop [fingerprint (<! fingerprint-channel)
          prints '()]
     (if (identical? fingerprint :stop)
       (>! analyser-channel prints)
       (recur (<! fingerprint-channel)
              (cons fingerprint prints))))))
