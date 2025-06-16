(ns dsl-mcp-server.registry
  (:require [cheshire.core :as json]
            [compojure.core :refer [GET POST routes]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [dsl-mcp-server.schema :as schema]))

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

(defn generate-tool-routes [dsl-name target compile-fn header-fn eyeball-fn]
  [(POST (str "/compile-" dsl-name "-" target) req
         (compile-dsl-handler compile-fn req))
   (GET (str "/header-" dsl-name "-" target) [] (header-dsl-handler header-fn))
   (POST (str "/eyeball-" dsl-name "-" target) req
         (eyeball-dsl-handler eyeball-fn req))])

;; Registry access functions
(defn get-tool-descriptions [registry]
  (let [tools (for [[dsl-name dsl-info] (:dsls registry)
                    [target target-info] (:targets dsl-info)]
                (generate-tool-description registry dsl-name target))]
    {:tools (remove nil? (flatten tools))
     :resources [{:name "overview"
                  :endpoint "/overview"
                  :description "Provides a high-level overview of the MCP server and its DSLs"}]}))

(defn get-tool-routes [registry]
  (get registry :routes []))

(defn get-prompt-routes [registry]
  (get registry :prompt-routes []))

;; Handler for getting a specific prompt
(defn get-prompt-handler
  "Creates a handler for getting a specific prompt"
  [registry prompt-name]
  (fn [request]
    (let [[prompt-type dsl-name target-name] (str/split prompt-name #"-")
          dsl (get-in registry [:dsls (keyword dsl-name)])
          target (get-in dsl [:targets target-name])
          available-prompts (keys (get-in target [:prompts]))
          prompt (get-in target [:prompts prompt-type])]
      (println "Debug - Prompt lookup:")
      (println "  prompt-name:" prompt-name)
      (println "  parsed components:" [prompt-type dsl-name target-name])
      (println "  available prompts:" available-prompts)
      (println "  found prompt:" prompt)
      (if prompt
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:prompt prompt})}
        {:status 404
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:error "Prompt not found"})}))))

(defn generate-prompt-routes [registry dsl-name target]
  [(GET (str "/prompts/compile-" dsl-name "-" target) [] 
        (get-prompt-handler registry (str "compile-" dsl-name "-" target)))
   (GET (str "/prompts/header-" dsl-name "-" target) [] 
        (get-prompt-handler registry (str "header-" dsl-name "-" target)))
   (GET (str "/prompts/eyeball-" dsl-name "-" target) [] 
        (get-prompt-handler registry (str "eyeball-" dsl-name "-" target)))])

;; Main registry update function
(defn add-dsl [registry dsl-name target & {:keys [description compile-fn header-fn eyeball-fn prompts]}]
  (let [updated-registry (-> registry
                            (assoc-in [:dsls dsl-name :targets target] 
                                     {:description description
                                      :compile-fn compile-fn
                                      :header-fn header-fn
                                      :eyeball-fn eyeball-fn
                                      :prompts prompts})
                            (update-in [:routes] concat 
                                      (generate-tool-routes dsl-name target compile-fn header-fn eyeball-fn))
                            (update-in [:prompt-routes] concat
                                      (generate-prompt-routes registry dsl-name target)))]
    (when-not (schema/validate-registry updated-registry)
      (throw (ex-info "Invalid registry structure after adding DSL"
                     {:errors (schema/explain-validation-error 
                              schema/registry-schema 
                              updated-registry)})))
    updated-registry)) 
