(ns dsl-mcp-server.plugin-loader
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [dsl-mcp-server.schema :as schema]
            [dsl-mcp-server.registry :as registry]
            [malli.core :as m]))


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

(defn load-plugin [plugin-dir plugin-name]
  (let [plugin-file (io/file plugin-dir plugin-name "dsl.clj")]
    (if (.exists plugin-file)
      (let [get-plugin-fn (load-file (.getPath plugin-file))
            plugin (get-plugin-fn tag-path)]
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

 
