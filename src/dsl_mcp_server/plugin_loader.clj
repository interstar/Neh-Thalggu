(ns dsl-mcp-server.plugin-loader
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [dsl-mcp-server.schema :as schema]
            [dsl-mcp-server.registry :as registry]
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

(defn load-plugin [plugin-dir plugin-name]
  (let [plugin-file (io/file plugin-dir plugin-name "dsl.clj")]
    (if (.exists plugin-file)
      (let [get-plugin-fn (load-file (.getPath plugin-file))
            ;; Create JAR classloader for this plugin
            jar-classloader (create-jar-classloader plugin-dir plugin-name)
            ;; Create load-java-class function for this plugin
            load-java-class-fn (partial load-java-class jar-classloader)
            ;; Call get-plugin with both functions, but handle backward compatibility
            plugin (try
                    ;; Try calling with both parameters (new plugins)
                    (get-plugin-fn tag-path load-java-class-fn)
                    (catch ArityException _
                      ;; Fall back to single parameter (existing plugins)
                      (get-plugin-fn tag-path)))]
        (if (m/validate schema/plugin-schema plugin)
          (-> plugin
              (assoc :name plugin-name)
              (update-in [:targets] #(update-vals % (fn [target]
                                                    (update target :prompts update-keys keyword)))))
          (do
            (println "Plugin" plugin-name "does not match schema:")
            (println (m/explain schema/plugin-schema plugin))
            (System/exit 1))))
      (do
        (println "Plugin file not found:" (.getAbsolutePath plugin-file))
        (System/exit 1)))))

(defn load-plugins [plugin-dir]
  (let [dir (io/file plugin-dir)]
    (if (.exists dir)
      (reduce (fn [reg plugin]
                (let [{:keys [name targets]} plugin]
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

 
