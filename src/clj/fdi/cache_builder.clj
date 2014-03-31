;; APPLICATION
;;
;; fdi - Find Duplicates Images
;;
;; FILE
;;
;; cache-builder.clj
;;
;; DESCRIPTION
;;
;; Reads image filenames from an input channel provided to the #'start
;; function, and sends a fingerprint bundle representing that image
;; to a success channel.  On failure, writes to the error channel
;; instead.
;;
;; The fingerprint bundle is a hash:
;;
;; { :filename    java.lang.String,
;;   :fingerprint byte[],
;;   :id          java.lang.String }
;;
;; The error bundle is also a hash:
;;
;; { :filename    java.lang.Stirng,
;;   :id          :error
;;   :fingerprint :error
;;   :error       java.lang.Exception }
;;
;; The builder reads from the input channel until it receives the keyword
;; :stop, whereupon it terminates.  It does not close any channel: that
;; is the responsibility of whichever module supplied the open channel
;; to #'start.  It does, however, send :stop to the output and error
;; channels.
;;
;; COPYRIGHT
;;
;; Copyright (C) 2014 Daniel Woods
;;
;; LICENSE
;;
;; GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

(ns fdi.cache-builder
  (:require [clojure.core.async :as async :refer [go chan alts! >!! >! <!]]
            [fdi.db-cache :as cache])
  (:use [clojure.tools.logging :only (error)])
  (:import [java.io File]
           [fdi FDI]))

(defn generate-fingerprint [#^String filename success-channel error-channel cache-lookup-fn]
  "Generates a byte-array fingerprint for the image file in the given filename.
Calls the success-callback with the fingerprint on successor, or error-callback
with no arguments if an error occurs.  Should never itself throw an error."
  (try
    (let [id (FDI/idString filename)
          fingerprint (cache-lookup-fn filename id)]
      (>!! success-channel (assoc fingerprint :filename filename)))
    (catch Exception e
      (error e)
      (>!! error-channel {:filename filename :id :error :fingerprint :error :error e}))))

(defn- agent-generate-fingerprint [state filename success-channel error-channel cache-lookup-fn]
  "Generates a byte-array fingerprint for the image file in the given filename.
The first argument, unused, is the state of the agent when the call is made.
It is otherwise identical to generate-fingerprint, which it calls."
  (generate-fingerprint filename success-channel error-channel cache-lookup-fn))

(defn start [filename-channel fingerprint-channel error-channel {:keys [agent-count disable-cache cache-file]}]
  ;; Starts reading from filename-channel, generating image fingerprints to be
  ;; sent to fingerprint-channel.  error-channel is written to for files
  ;; which cannot be read.
  (let [thread-count (or agent-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize))
        agent-pool (map #(agent %) (range thread-count))
        cache-state (if disable-cache nil (cache/load cache-file))]
    (go
     (loop [message (<! filename-channel)]
       (if (= message :stop)
         (do
           (apply await agent-pool)
           (if-not disable-cache (cache/save cache-state))
           (>! fingerprint-channel :stop)
           (>! error-channel :stop))
         (let [agent-index (rem (Math/abs (.hashCode #^String message)) thread-count)
               selected-agent (nth agent-pool agent-index)]
           (send
            selected-agent
            agent-generate-fingerprint
            message fingerprint-channel error-channel (if disable-cache
                                                        (fn [filename id]
                                                          {:fingerprint (FDI/fingerprint filename)
                                                           :size (.length (File. filename))
                                                           :id id})
                                                        (fn [filename id]
                                                          (cache/find-if-absent-put
                                                           cache-state id
                                                           #(hash-map
                                                             :fingerprint (FDI/fingerprint filename)
                                                             :size (.length (File. filename))
                                                             :id id)))))
           (recur (<! filename-channel))))))))
