;; APPLICATION
;;
;; fdi - Find Duplicates Images
;;
;; FILE
;;
;; collator.clj
;;
;; DESCRIPTION
;;
;; Reads image fingerprints from an input channel and accumulates them
;; until the keyword :stop is received, at which point it sends the
;; complete collection to an output channel.
;;
;; The fingerprint bundle is a hash:
;;
;; { :filename    java.lang.String,
;;   :fingerprint byte[],
;;   :id          java.lang.String }
;;
;; COPYRIGHT
;;
;; Copyright (C) 2014 Daniel Woods
;;
;; LICENSE
;;
;; GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

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
