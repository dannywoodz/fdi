(ns fdi.db-cache
  (:require [clojure.java.jdbc :as db])
  (:refer-clojure :exclude [load]))

(defn load []
  (let [spec {:subprotocol "sqlite"
              :subname "cache.sqlite"}
        object {:data (atom
                       (reduce (fn [c t]
                                 (assoc c (:id t) t))
                               {}
                               (db/query spec ["select id,fingerprint,size from cache"])))
                :spec spec}]
    object))

(defn save [{:keys [data spec] :as cache}]
  (db/with-db-transaction [connection spec]
    (db/delete! spec :cache [])
    (apply db/insert! spec :cache (vals @data))
    cache))

(defn- cache-hit [{id :id :as record}]
  (println "Cache HIT for" id)
  record)

(defn- cache-miss [data-atom id genfn]
  (println "Cache MISS for" id)
  (let [record (genfn)]
    (swap! data-atom assoc id record)
    record))

(defn find-if-absent-put [{data :data :as cache} id genfn]
  (let [record (get @data id)]
    (if record
      (cache-hit record)
      (cache-miss data id genfn))))
