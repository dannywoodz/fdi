(ns fdi.cache-builder
  (:require [clojure.core.async :as async :refer :all])
  (:import [java.util.concurrent Executors]
           [magick ImageInfo MagickImage]
           [fdi FileID]))

(defn- unsafe-generate-fingerprint [filename]
  "Generates a byte-array fingerprint for the image file in the given filename.
May throw an exception if there is a problem generating the print."
  (println "Generating fingerprint for" filename)
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

(defn generate-fingerprint [filename success-callback error-callback]
  "Generates a byte-array fingerprint for the image file in the given filename.
Calls the success-callback with the fingerprint on successor, or error-callback
with no arguments if an error occurs.  Should never itself throw an error."
  (try
    (println "Generating fingerprint for" filename)
    (let [fingerprint (unsafe-generate-fingerprint filename)]
      (println "Fingerprint for" filename "is" fingerprint)
      (success-callback fingerprint))
    (catch Exception e
      (error-callback))))

(defn- agent-generate-fingerprint [state filename success-callback error-callback]
  "Generates a byte-array fingerprint for the image file in the given filename.
The first argument, unused, is the state of the agent when the call is made.
It is otherwise identical to generate-fingerprint, which it calls."
  (generate-fingerprint filename success-callback error-callback))

(defn start [filename-channel fingerprint-channel error-channel]
  ;; Starts reading from filename-channel, generating image fingerprints to be
  ;; sent to fingerprint-channel.  error-channel is written to for files
  ;; which cannot be read.
  (let [cpu-count (.availableProcessors (Runtime/getRuntime))
        agent-pool (clojure.core/map #(agent %) (range cpu-count))
        executor (Executors/newFixedThreadPool cpu-count)]
    (go
     (println "Starting cache builder")
     (loop [filename (<! filename-channel)]
       (println "Caching" filename)
       (cond (identical? filename :stop)
             (do
               (>! fingerprint-channel :stop)
               (>! error-channel :stop))
             :default
             (do
               (try
                 (let [id (FileID/idString filename)
                       agent-index (rem (Math/abs (.hashCode id)) cpu-count)
                       _ (println "Using agent" agent-index)
                       selected-agent (nth agent-pool agent-index)]
                   (println "Sending to agent" selected-agent)
                   (send-via executor
                             selected-agent
                             agent-generate-fingerprint
                             filename
                             (fn [fp] (>!! fingerprint-channel
                                           {:filename filename
                                            :key id
                                            :fingerprint fp}))
                             (fn [] (>!! error-channel
                                         {:filename filename
                                          :key id
                                          :fingerprint :error}))))
                 (catch java.io.IOException e
                   (>! error-channel {:filename filename
                                      :key :error
                                      :fingerprint :error
                                      :error e})))
               (recur (<! filename-channel))))))))


  
