(ns fdi.cache-builder
  (:require [clojure.core.async :as async :refer [go chan alts! >!! >! <!]]
            [fdi.db-cache :as cache])
  (:import [java.io File]
           [fdi FileID]))

(defn generate-fingerprint [#^String filename success-channel error-channel cache-state]
  "Generates a byte-array fingerprint for the image file in the given filename.
Calls the success-callback with the fingerprint on successor, or error-callback
with no arguments if an error occurs.  Should never itself throw an error."
  (try
    (let [id (FileID/idString filename)
          fingerprint (cache/find-if-absent-put
                       cache-state id
                       #(hash-map
                         :fingerprint (FileID/fingerprint filename)
                         :size (.length (File. filename))
                         :id id))]
      (>!! success-channel (assoc fingerprint :filename filename)))
    (catch Exception e
      (println e)
      (>!! error-channel {:filename filename :id :error :fingerprint :error :error e}))))

(defn- agent-generate-fingerprint [state filename success-channel error-channel cache]
  "Generates a byte-array fingerprint for the image file in the given filename.
The first argument, unused, is the state of the agent when the call is made.
It is otherwise identical to generate-fingerprint, which it calls."
  (generate-fingerprint filename success-channel error-channel cache))

(defn start [filename-channel fingerprint-channel error-channel]
  ;; Starts reading from filename-channel, generating image fingerprints to be
  ;; sent to fingerprint-channel.  error-channel is written to for files
  ;; which cannot be read.
  (let [thread-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize)
        agent-pool (map #(agent %) (range thread-count))
        cache-state (cache/load)]
    (go
     (loop [message (<! filename-channel)]
       (if (= message :stop)
         (do
           (apply await agent-pool)
           (cache/save cache-state)
           (>! fingerprint-channel :stop)
           (>! error-channel :stop))
         (let [agent-index (rem (Math/abs (.hashCode #^String message)) thread-count)
               selected-agent (nth agent-pool agent-index)]
           (send
            selected-agent
            agent-generate-fingerprint
            message fingerprint-channel error-channel cache-state)
           (recur (<! filename-channel))))))))
