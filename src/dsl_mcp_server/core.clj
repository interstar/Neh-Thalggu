(ns dsl-mcp-server.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET POST routes]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [dsl-mcp-server.registry :as registry]
            [dsl-mcp-server.dsls.speak :as speak]
            [dsl-mcp-server.dsls.ui :as ui]
            [clojure.string :as string]))

(declare DSLregistry)

;; Load prompts from files
(defn load-prompt [filename]
  (let [resource (io/resource (str "prompts/" filename))]
    (if resource
      (-> resource
          slurp
          (json/parse-string true))
      (throw (ex-info (str "Prompt file not found: " filename)
                     {:filename filename})))))

;; Handler for listing available prompts
(defn list-prompts-handler [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string (registry/list-prompts DSLregistry))})

;; Debug endpoint to inspect the DSLregistry's :prompts map
(defn debug-prompts-handler [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string (get-in DSLregistry [:prompts]))})

;; Define the DSL registry
(def DSLregistry
  (let [reg (-> {}
                 (registry/add-dsl "speak" "haxe"
                                    :description "A DSL for generating speaker classes"
                                    :compile-fn #'speak/compile-to-haxe
                                    :header-fn #'speak/get-header
                                    :eyeball-fn #'speak/eyeball-haxe
                                    :prompts {:compile "compile-speak-haxe.json"
                                             :header "header-speak-haxe.json"})
                 (registry/add-dsl "ui" "jinja2"
                                    :description "A DSL for generating UI layouts"
                                    :compile-fn #'ui/compile-to-jinja2
                                    :header-fn #'ui/get-header
                                    :eyeball-fn #'ui/eyeball-jinja2
                                    :prompts {:compile "compile-ui-jinja2.json"
                                             :header "header-ui-jinja2.json"}))]
    (println "DEBUG: DSLregistry after construction:\n" (with-out-str (clojure.pprint/pprint reg)))
    reg))

;; MCP endpoint routes
(defroutes app-routes
  ;; MCP prompt endpoints
  (GET "/prompts/list" [] list-prompts-handler)
  
  ;; Debug endpoint
  (GET "/debug/prompts" [] debug-prompts-handler)
  
  ;; Prompt routes from registry
  (apply routes (registry/get-prompt-routes DSLregistry))
  
  ;; Root endpoint
  (GET "/" [] {:status 200
               :headers {"Content-Type" "application/json"}
               :body (json/generate-string (registry/get-tool-descriptions DSLregistry))})
  
  ;; Tool routes from registry
  (apply routes (registry/get-tool-routes DSLregistry))
  
  ;; Overview endpoint
  (GET "/overview" []
    (let [explanation (-> (io/resource "overview.md") slurp)
          dsls (->> DSLregistry
                    :dsls
                    (map (fn [[dsl-name dsl-info]]
                           (str "- **" dsl-name "**: " (get-in dsl-info [:targets (first (keys (:targets dsl-info))) :description]))))
                    (string/join "\n"))]
      (json/generate-string {:explanation explanation :DSLs dsls})))
  
  ;; Not found handler
  (route/not-found {:status 404 :body "Not Found"}))

(defn -main [& args]
  (println "Starting DSL MCP server on port 3000...")
  (jetty/run-jetty app-routes {:port 3000})) 