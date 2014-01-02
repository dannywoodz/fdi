(ns fdi.cache-builder
  (:require [clojure.core.async :as async :refer [go chan alts! >!! >!]])
  (:import [java.util.concurrent Executors]
           [java.io File]
           [fdi FileID]))

(defn generate-fingerprint [filename feedback-channel]
  "Generates a byte-array fingerprint for the image file in the given filename.
Calls the success-callback with the fingerprint on successor, or error-callback
with no arguments if an error occurs.  Should never itself throw an error."
  (try
    (let [id (FileID/idString filename)
          fingerprint (FileID/fingerprint filename)
          size (.length (File. filename))]
      (>!! feedback-channel {:filename filename :id id :fingerprint fingerprint :size size}))
    (catch Exception e
      (>!! feedback-channel {:filename filename :id :error :fingerprint :error :error e}))))

(defmacro after [delay & body]
  `(do
     (Thread/sleep ~delay)
     ~@body))

(defn- agent-generate-fingerprint [state filename feedback-channel]
  "Generates a byte-array fingerprint for the image file in the given filename.
The first argument, unused, is the state of the agent when the call is made.
It is otherwise identical to generate-fingerprint, which it calls."
  (if (= filename :stop)
    (after 5000 (>!! feedback-channel :stop))
    (generate-fingerprint filename feedback-channel)))

(defn start [filename-channel fingerprint-channel error-channel]
  ;; Starts reading from filename-channel, generating image fingerprints to be
  ;; sent to fingerprint-channel.  error-channel is written to for files
  ;; which cannot be read.
  (let [cpu-count (.availableProcessors (Runtime/getRuntime))
        agent-pool (clojure.core/map #(agent %) (range cpu-count))
        executor (Executors/newFixedThreadPool cpu-count)
        feedback-channel (chan)]
    (go
     (loop [[message channel] (alts! [filename-channel feedback-channel])]
       (cond
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; feedback channel activity
        (= channel feedback-channel)
        (cond
         (= message :stop)
         (do
           (.shutdown executor)
           (>! fingerprint-channel :stop)
           (>! error-channel :stop))
         :default
         (let [{error :error} message]
           (if error
             (>! error-channel message)
             (>! fingerprint-channel message))
           (recur (alts! [filename-channel feedback-channel]))))
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; new filename received for caching
        (= channel filename-channel)
        (do
          (try
            (let [agent-index (rem (Math/abs (.hashCode message)) cpu-count)
                  selected-agent (nth agent-pool agent-index)]
              (send-via executor selected-agent
                        agent-generate-fingerprint
                        message feedback-channel))
            (catch java.io.IOException e
              (>! error-channel {:filename message
                                 :key :error
                                 :fingerprint :error
                                 :error e})))
          (recur (alts! [filename-channel feedback-channel]))))))))
