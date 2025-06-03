(ns dsl-mcp-server.registry
  (:require [cheshire.core :as json]
            [compojure.core :refer [GET POST routes]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

;; Generic handlers for all DSLs
(defn compile-dsl-handler [compile-fn req]
  (let [body (json/parse-string (slurp (:body req)) true)
        dsl-input (:dsl body)
        result (compile-fn dsl-input)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string result)}))

(defn header-dsl-handler [header-fn]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string (header-fn))})

;; Helper functions for registry updates
(defn generate-tool-description [dsl-name target description]
  [{:name (str "compile-" dsl-name "-" target)
    :endpoint (str "/compile-" dsl-name "-" target)
    :description (str "Compiles " description " to " target)
    :input
    {:type "json"
     :schema
     {:type "object"
      :properties
      {:dsl
       {:type "string"
        :description "The DSL input to compile."}}
      :required ["dsl"]}}
    :output
    {:type "json"
     :schema
     {:type "object"
      :properties
      {:success
       {:type "boolean"
        :description "Whether the compilation was successful."}
       :haxeCode
       {:type "string"
        :description "The generated Haxe code."}
       :error
       {:type "string"
        :description "Error message if compilation failed."}}
      :required ["success"]}}}
   {:name (str "header-" dsl-name "-" target)
    :endpoint (str "/header-" dsl-name "-" target)
    :description (str "Gets the required header code for " description " targeting " target)
    :input
    {:type "json"
     :schema
     {:type "object"
      :properties {}
      :required []}}
    :output
    {:type "json"
     :schema
     {:type "object"
      :properties
      {:success
       {:type "boolean"
        :description "Whether the request was successful."}
       :haxeCode
       {:type "string"
        :description "The required header code."}}
      :required ["success"]}}}])

(defn generate-prompt-entries [dsl-name target prompts]
  {(str "compile-" dsl-name "-" target) (str "prompts/" (get prompts :compile))
   (str "header-" dsl-name "-" target) (str "prompts/" (get prompts :header))})

(defn generate-tool-routes [dsl-name target compile-fn header-fn]
  [(POST (str "/compile-" dsl-name "-" target) req (compile-dsl-handler compile-fn req))
   (GET (str "/header-" dsl-name "-" target) [] (header-dsl-handler header-fn))])

;; Registry access functions
(defn get-tool-descriptions [registry]
  {:tools (get registry :tools [])})

(defn list-prompts [registry]
  {:prompts (vals (get registry :prompts {}))})

(defn get-prompt [registry name]
  (get-in registry [:prompts name]))

(defn get-tool-routes [registry]
  (get registry :routes []))

(defn get-prompt-routes [registry]
  (get registry :prompt-routes []))

;; Handler for getting a specific prompt
(defn get-prompt-handler [filename]
  (let [resource (io/resource filename)]
    (if resource
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (slurp resource)}
      {:status 404
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:error (str "Prompt file not found: " filename)})})))

(defn generate-prompt-routes [prompt-entries]
  (map (fn [[name filename]]
         (GET (str "/prompts/" name) [] 
              (get-prompt-handler filename)))
       prompt-entries))

;; Main registry update function
(defn add-dsl [registry dsl-name target & {:keys [description compile-fn header-fn prompts]}]
  (let [prompt-entries (generate-prompt-entries dsl-name target prompts)]
    (-> registry
        (assoc-in [:dsls dsl-name :targets target] 
                  {:description description
                   :compile-fn compile-fn
                   :header-fn header-fn
                   :prompts prompts})
        (update-in [:routes] concat 
                   (generate-tool-routes dsl-name target compile-fn header-fn))
        (update-in [:tools] concat 
                   (generate-tool-description dsl-name target description))
        (update-in [:prompts] merge prompt-entries)
        (update-in [:prompt-routes] concat
                   (generate-prompt-routes prompt-entries))))) 