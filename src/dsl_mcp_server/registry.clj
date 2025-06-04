(ns dsl-mcp-server.registry
  (:require [cheshire.core :as json]
            [compojure.core :refer [GET POST routes]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

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

(defn eyeball-dsl-handler [eyeball-fn req]
  (let [body (json/parse-string (slurp (:body req)) true)
        code (:code body)
        result (eyeball-fn code)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string result)}))

;; Helper functions for registry updates
(defn generate-tool-description [registry dsl-name target-lang]
  (let [dsl-key (keyword dsl-name)
        target-key (keyword target-lang)
        dsl-info (get-in registry [:dsls dsl-key])
        target-info (get-in dsl-info [:targets target-key])]
    [{:name (str "compile-" dsl-name "-" target-lang)
      :endpoint (str "/compile-" dsl-name "-" target-lang)
      :description (str "Compiles " dsl-name " DSL to " target-lang)
      :input {:type "json"
              :schema {:type "object"
                      :properties {:dsl {:type "string"
                                       :description "The DSL input to compile"}}
                      :required ["dsl"]}}
      :output {:type "json"
               :schema {:type "object"
                       :properties {:success {:type "boolean"
                                            :description "Whether compilation succeeded"}
                                  :code {:type "string"
                                        :description "The generated code"}
                                  :notes {:type "string"
                                         :description "Additional information about the generated code"}
                                  :warnings {:type "array"
                                           :items {:type "string"}
                                           :description "Any warnings about the generated code"}
                                  :error {:type "string"
                                         :description "Error message if compilation failed"}}
                       :required ["success" "code"]}}}
     {:name (str "header-" dsl-name "-" target-lang)
      :endpoint (str "/header-" dsl-name "-" target-lang)
      :description (str "Gets the required header code for " dsl-name " DSL targeting " target-lang)
      :input {:type "json"
              :schema {:type "object"
                      :properties {}
                      :required []}}
      :output {:type "json"
               :schema {:type "object"
                       :properties {:success {:type "boolean"
                                            :description "Whether header generation succeeded"}
                                  :code {:type "string"
                                        :description "The required header code"}
                                  :notes {:type "string"
                                         :description "Additional information about the header code"}}
                       :required ["success" "code"]}}}
     {:name (str "eyeball-" dsl-name "-" target-lang)
      :endpoint (str "/eyeball-" dsl-name "-" target-lang)
      :description (str "Performs sanity checks on generated " target-lang " code for " dsl-name " DSL")
      :input {:type "json"
              :schema {:type "object"
                      :properties {:code {:type "string"
                                        :description "The code to check"}}
                      :required ["code"]}}
      :output {:type "json"
               :schema {:type "object"
                       :properties {:status {:type "string"
                                           :description "Either 'seems ok' or 'issues'"}
                                  :issues {:type "array"
                                         :items {:type "string"}
                                         :description "List of issues found, if any"}
                                  :notes {:type "string"
                                         :description "Additional information about the code check"}}
                       :required ["status" "issues"]}}}]))

(defn generate-prompt-entries [dsl-name target prompts]
  {(str "compile-" dsl-name "-" target) (str "prompts/" (get prompts :compile))
   (str "header-" dsl-name "-" target) (str "prompts/" (get prompts :header))})

(defn generate-tool-routes [dsl-name target compile-fn header-fn eyeball-fn]
  [(POST (str "/compile-" dsl-name "-" target) req (compile-dsl-handler compile-fn req))
   (GET (str "/header-" dsl-name "-" target) [] (header-dsl-handler header-fn))
   (POST (str "/eyeball-" dsl-name "-" target) req (eyeball-dsl-handler eyeball-fn req))])

;; Registry access functions
(defn get-tool-descriptions [registry]
  (let [tools (for [[dsl-name dsl-info] (:dsls registry)
                    [target target-info] (:targets dsl-info)]
                (generate-tool-description registry dsl-name target))]
    {:tools (remove nil? (flatten tools))
     :prompts (get registry :prompts {})
     :resources [{:name "overview"
                  :endpoint "/overview"
                  :description "Provides a high-level overview of the MCP server and its DSLs"}]}))

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
(defn add-dsl [registry dsl-name target & {:keys [description compile-fn header-fn eyeball-fn prompts]}]
  (let [prompt-entries (generate-prompt-entries dsl-name target prompts)]
    (-> registry
        (assoc-in [:dsls dsl-name :targets target] 
                 {:description description
                  :compile-fn compile-fn
                  :header-fn header-fn
                  :eyeball-fn eyeball-fn
                  :prompts prompts})
        (update-in [:routes] concat 
                  (generate-tool-routes dsl-name target compile-fn header-fn eyeball-fn))
        (update-in [:prompts] merge prompt-entries)
        (update-in [:prompt-routes] concat
                  (generate-prompt-routes prompt-entries))
        (update-in [:tools] concat
                  (generate-tool-description registry dsl-name target))))) 