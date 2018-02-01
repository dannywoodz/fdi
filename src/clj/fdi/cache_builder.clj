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
           [java.util.concurrent Executors]
           [fdi FDI]))

(defn generate-fingerprint [generator #^String filename success-channel error-channel]
  "Generates a byte-array fingerprint for the image file in the given filename.
Calls the success-callback with the fingerprint on successor, or error-callback
with no arguments if an error occurs.  Should never itself throw an error."
  (try
    (let [id (FDI/idString filename)
          fingerprint (generator filename id)]
      (>!! success-channel (assoc fingerprint :filename filename)))
    (catch Exception e
      (error e)
      (>!! error-channel {:filename filename :id :error :fingerprint :error :error e}))))

(defn- agent-generate-fingerprint [state generator filename success-channel error-channel]
  "Generates a byte-array fingerprint for the image file in the given filename.
The first argument, unused, is the state of the agent when the call is made.
It is otherwise identical to generate-fingerprint, which it calls."
  (generate-fingerprint generator filename success-channel error-channel))

(defn- fingerprint [filename id]
  (hash-map :fingerprint (FDI/fingerprint filename)
            :size (.length (File. filename))
            :id id))

(defn- fingerprint-generator [cache-state]
  (if (nil? cache-state)
    #'fingerprint
    (fn [filename id]
      (cache/find-if-absent-put
       cache-state id
       (fn [] (fingerprint filename id))))))

(defn start [filename-channel fingerprint-channel error-channel {:keys [agent-count disable-cache cache-file]}]
  ;; Starts reading from filename-channel, generating image fingerprints to be
  ;; sent to fingerprint-channel.  error-channel is written to for files
  ;; which cannot be read.
  (let [cache-state (if disable-cache nil (cache/load cache-file))
        fingerprinter (fingerprint-generator cache-state)
        pool (Executors/newFixedThreadPool agent-count)]
    (go
      (loop [filename (<! filename-channel)
             futures []]
        (if (= filename :stop)
          (do
            (doseq [f futures] (.get f))
            (.shutdown pool)
            (if-not disable-cache (locking cache-state (cache/save cache-state)))
            (>! fingerprint-channel :stop)
            (>! error-channel :stop))
          (recur (<! filename-channel)
                 (doall
                  (filter #(not (.isDone %))
                          (conj futures (.submit pool #^Callable (fn [] (generate-fingerprint fingerprinter filename fingerprint-channel error-channel))))))))))))

