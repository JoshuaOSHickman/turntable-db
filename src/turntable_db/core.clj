(ns turntable-db.core
  (:use [clojure.java.shell :only [sh]])
  (:import (java.security MessageDigest Security)))

(def ^:dynamic *root-data-dir* "data/")

(def digest (MessageDigest/getInstance "SHA-256"))
(defn b36 [hbytes] (.toString (BigInteger. 1 hbytes) 36))
(defn secure-hash [s]
  (.update digest (.getBytes s))
  (b36 (.digest digest)))

;;;Folder where the data for this key is kept. TODO: Sanitize the key and append a hash to keep it unique.
(defn folder-for-key [key]
  (str *root-data-dir* (.replaceAll key "\\W" "") "-" (secure-hash key)))

(defn file-for-versions [key]
  (str (folder-for-key key) "/versions.history"))

(defn file-for-current [key]
  (str (folder-for-key key) "/current.hash"))

(defn file-to-store [key version]
  (str (folder-for-key key) "/" version ".clj"))

; todo make this re-entrant, get rid of key-exists, that will solve the race condition
(defn make-directory [filename]
  (if (not (.exists (java.io.File. *root-data-dir*)))
    (.mkdir (java.io.File. *root-data-dir*))) ; have this be marginally more clever, we shouldn't need to check this
  (.mkdir (java.io.File. filename)))

(defn initialize [key]
  (make-directory (folder-for-key key))
  (spit (file-for-versions key) ""))

(defn key-exists? [key]
  (.exists (java.io.File. (folder-for-key key))))

(defn write-data [key data]
  (if (not (key-exists? key)) (initialize key))
  (let [versions-file (file-for-versions key)
        versions (.split (slurp versions-file) "\n")
        data-string (pr-str data)
        this-version (secure-hash data-string)
        storage-file (file-to-store key this-version)]
    (spit storage-file data-string) ; store current data
    (spit versions-file (str this-version "\n") :append true)); point "current" to the new
  true)

(defn read-data [key]
  ; the double file read is because symlinks don't have a java api until Java 7
  (first (all-versions key)))

; Note: is lazy map so things like revert are fast.
(defn all-versions [key]
  (let [version-hashes (.split (slurp (file-for-versions key)) "\n")]
    (map #(read-string (slurp (file-to-store key %))) (reverse (seq version-hashes)))))

(defn revert [key] ; not really a super good idea to revert without knowing what you're reverting to.
  (write-data key (second (all-versions key))))

(time
 (do (write-data "guesswork" {:total :recall})
     (read-data "guesswork")))