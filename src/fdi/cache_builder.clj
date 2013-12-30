(ns fdi.cache-builder
  (:require [clojure.core.async :as async :refer :all])
  (:import [java.util.concurrent Executors]
           [magick ImageInfo MagickImage]
           [fdi FileID]))

(defn- unsafe-generate-fingerprint [filename]
  "Generates a byte-array fingerprint for the image file in the given filename.
May throw an exception if there is a problem generating the print."
  (let [cleanup (atom [])]
    (try
      (let [image-info (ImageInfo. filename)
            image (MagickImage. image-info)
            _ (swap! cleanup conj image)
            scaled (-> (doto image .normalizeImage)
                       (.scaleImage 4 4))
            _ (swap! cleanup conj scaled)]
        (.setMagick scaled "rgb")
        (.imageToBlob scaled image-info))
      (finally
        (doseq [i @cleanup]
          (.destroyImages i))))))

(defn generate-fingerprint [filename feedback-channel]
  "Generates a byte-array fingerprint for the image file in the given filename.
Calls the success-callback with the fingerprint on successor, or error-callback
with no arguments if an error occurs.  Should never itself throw an error."
  (try
    (let [id (FileID/idString filename)
          fingerprint (unsafe-generate-fingerprint filename)]
      (println "Fingerprint for" filename "is" fingerprint)
      (>!! feedback-channel {:filename filename :id id :fingerprint fingerprint})
      (println "Wrote fingerprint to channel"))
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
    (after 1000 (>!! feedback-channel :stop))
    (generate-fingerprint filename feedback-channel)))

(defn start [filename-channel fingerprint-channel error-channel]
  ;; Starts reading from filename-channel, generating image fingerprints to be
  ;; sent to fingerprint-channel.  error-channel is written to for files
  ;; which cannot be read.
  (let [cpu-count (.availableProcessors (Runtime/getRuntime))
        agent-pool (clojure.core/map #(agent %) (range cpu-count))
        executor (Executors/newFixedThreadPool cpu-count)
        feedback-channel (chan)]
    (println "Starting cache builder")
    (go
     (loop [[message channel] (alts! [filename-channel feedback-channel])]
       (println "Received" message "on" (if (identical? channel filename-channel)
                                          "filename channel"
                                          "feedback channel"))
       (cond
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; feedback channel activity
        (= channel feedback-channel)
        (cond
         (= message :stop)
         (do (>! fingerprint-channel :stop)
             (>! error-channel :stop)
             (.shutdown executor))
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
              (println "Sending" message "to" agent-index)
              (send-via executor selected-agent
                        agent-generate-fingerprint
                        message feedback-channel))
            (catch java.io.IOException e
              (>! error-channel {:filename message
                                 :key :error
                                 :fingerprint :error
                                 :error e})))
          (recur (alts! [filename-channel feedback-channel]))))))))





  
