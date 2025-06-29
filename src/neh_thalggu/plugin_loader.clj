(ns neh-thalggu.plugin-loader
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [neh-thalggu.schema :as schema]
            [neh-thalggu.registry :as registry]
            [malli.core :as m])
  (:import (clojure.lang ArityException)))

(defn tag-path
  "Traverses a Hiccup-like vector tree by matching tag keywords.
   Each step in `path` is a keyword like :div or :ul.
   Returns the first node that matches the complete path, or nil if not found."
  [hiccup path]
  (letfn [(find-path [node remaining-path]
            (cond
              ;; If we've used up all path elements, we found our target
              (empty? remaining-path) node
              
              ;; If current node isn't a vector, can't match path
              (not (vector? node)) nil
              
              ;; Try to find next tag in children
              :else (let [[tag & children] node
                         next-tag (first remaining-path)]
                      (if (= tag next-tag)
                        (if (= 1 (count remaining-path))
                          ;; If this is the last tag in the path, return the matching node
                          node
                          ;; Otherwise, keep searching in children
                          (some #(find-path % (rest remaining-path)) children))
                        ;; Tag doesn't match, try next sibling
                        nil))))]
    (find-path hiccup path)))

(defn create-jar-classloader [plugin-dir plugin-name]
  "Creates a URLClassLoader for JAR files in the plugin directory."
  (let [plugin-path (io/file plugin-dir plugin-name)
        jar-files (->> (.listFiles plugin-path)
                      (filter #(.isFile %))
                      (filter #(.endsWith (.getName %) ".jar")))]
    (if (seq jar-files)
      (let [urls (map #(.toURL %) jar-files)]
        (java.net.URLClassLoader. (into-array java.net.URL urls)))
      nil)))

(defn load-java-class [classloader class-name]
  "Loads a Java class using the provided classloader."
  (try
    (if classloader
      (.loadClass classloader class-name)
      (Class/forName class-name))
    (catch Exception e
      (println "Failed to load Java class" class-name ":" (.getMessage e))
      nil)))

(defn load-clojure-namespace [classloader namespace-sym]
  "Loads a Clojure namespace using the provided classloader."
  (try
    (if classloader
      ;; Add the classloader to the current thread's context classloader
      (let [original-classloader (.getContextClassLoader (Thread/currentThread))]
        (.setContextClassLoader (Thread/currentThread) classloader)
        (try
          (require namespace-sym)
          (.setContextClassLoader (Thread/currentThread) original-classloader)
          true
          (catch Exception e
            (.setContextClassLoader (Thread/currentThread) original-classloader)
            (throw e))))
      (require namespace-sym))
    (catch Exception e
      (println "Failed to load Clojure namespace" namespace-sym ":" (.getMessage e))
      nil)))

(defn load-plugin [plugin-dir plugin-name]
  (let [plugin-file (io/file plugin-dir plugin-name "dsl.clj")]
    (if (.exists plugin-file)
      (let [;; Load the plugin file and get the function map
            plugin-fns (load-file (.getPath plugin-file))
            
            ;; Stage 1: Get metadata (no load functions needed)
            metadata ((:get-metadata plugin-fns))]
        
        ;; Validate metadata
        (let [metadata-validation (schema/validate-plugin-metadata metadata)]
          (if (:valid metadata-validation)
            (let [;; Stage 2: Create appropriate load functions based on metadata
                  jar-classloader (create-jar-classloader plugin-dir plugin-name)
                  load-fns (case (:type metadata)
                             :java-jar {:load-java-class (partial load-java-class jar-classloader)}
                             :clojure-jar {:load-java-class (partial load-java-class jar-classloader)
                                          :load-clojure-namespace (partial load-clojure-namespace jar-classloader)}
                             :native {})
                  
                  ;; Stage 3: Get full plugin (with load functions)
                  plugin ((:get-plugin plugin-fns) tag-path load-fns)]
              
              ;; Validate full plugin
              (let [plugin-validation (schema/validate-plugin plugin)]
                (if (:valid plugin-validation)
                  (-> plugin
                      (update :metadata assoc :name plugin-name)
                      (update-in [:targets] #(update-vals % (fn [target]
                                                            (update target :prompts update-keys keyword)))))
                  (do
                    (println "Plugin" plugin-name "does not match schema:")
                    (println (:errors plugin-validation))
                    (System/exit 1)))))
            (do
              (println "Plugin metadata for" plugin-name "does not match schema:")
              (println (:errors metadata-validation))
              (System/exit 1)))))
      (do
        (println "Plugin file not found:" (.getAbsolutePath plugin-file))
        (System/exit 1)))))

(defn load-plugins [plugin-dir]
  (let [dir (io/file plugin-dir)]
    (if (.exists dir)
      (reduce (fn [reg plugin]
                (let [{:keys [metadata targets]} plugin
                      {:keys [name]} metadata]
                  (reduce (fn [reg [target-name target-info]]
                           (registry/add-dsl reg name target-name
                                           :description (:description target-info)
                                           :compile-fn (:compile-fn target-info)
                                           :header-fn (:header-fn target-info)
                                           :eyeball-fn (:eyeball-fn target-info)
                                           :prompts (:prompts target-info)))
                         reg
                         targets)))
              {:dsls {} :prompts {} :routes [] :prompt-routes []}
              (->> (.listFiles dir)
                   (filter #(.isDirectory %))
                   (map #(.getName %))
                   (map #(load-plugin plugin-dir %))
                   (filter some?)))
      (do
        (println "Plugin directory not found:" plugin-dir)
        (System/exit 1)))))

 
