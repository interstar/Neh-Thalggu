(ns dsl-mcp-server.schema
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]))

(def header-result-schema
  [:map
   [:success boolean?]
   [:code string?]
   [:notes string?]
   [:warning string?]])

(def compile-result-schema
  [:map
   [:success boolean?]
   [:code [:sequential string?]]
   [:notes string?]
   [:warning string?]
   [:error {:optional true} string?]])

(def eyeball-result-schema
  [:map
   [:status [:enum "seems ok" "issues"]]
   [:issues [:vector string?]]
   [:notes string?]])

;; Combined Plugin Schema (metadata + implementation)
(def plugin-schema
  [:map
   [:name string?]
   [:description string?]
   [:version string?]
   [:author string?]
   [:grammar
    [:map
     [:rules [:map-of string? string?]]
     [:start string?]]]
   [:targets
    [:map-of
     string?
     [:map
      [:description string?]
      [:compile-fn fn?]
      [:header-fn fn?]
      [:eyeball-fn fn?]]]]])

;; Registry Schema
(def registry-schema
  [:map
   [:dsls
    [:map-of
     string?
     [:map
      [:targets
       [:map-of
        string?
        [:map
         [:description string?]
         [:compile-fn fn?]
         [:header-fn fn?]
         [:eyeball-fn fn?]
         [:prompts [:map-of string? string?]]]]]]]]
   [:prompts
    [:map-of
     string?
     string?]]
   [:routes
    [:sequential fn?]]
   [:prompt-routes
    [:sequential fn?]]])

;; Helper functions for validation
(defn validate-plugin
  "Validates plugin against the schema"
  [plugin]
  (let [result (m/validate plugin-schema plugin)]
    (if (true? result)
      {:valid true}
      {:valid false
       :errors (me/humanize (m/explain plugin-schema plugin))})))

(defn validate-registry
  "Validates registry against the schema"
  [registry]
  (m/validate registry-schema registry))

(defn explain-validation-error
  "Returns human-readable explanation of validation errors"
  [schema value]
  (-> (m/explain schema value)
      (me/humanize)))

;; Example usage:
(comment
  ;; Validate plugin
  (validate-plugin
   {:name "test-plugin"
    :description "A test plugin"
    :targets {"haxe" {:description "Haxe target"
                      :compile-fn (fn [x] x)
                      :header-fn (fn [] "header")
                      :eyeball-fn (fn [x] x)}}
    :grammar "grammar = ..."
    :parser (fn [x] x)
    :generate-header (fn [] "header")
    :generate-template (fn [x] x)
    :handle-parse-error (fn [x] x)})
  
  ;; Validate registry
  (validate-registry
   {:dsls {"test-dsl" {:targets {"haxe" {:description "Haxe target"
                                         :compile-fn (fn [x] x)
                                         :header-fn (fn [] "header")
                                         :eyeball-fn (fn [x] x)}}}}
    :prompts {"compile-test-dsl-haxe" "prompts/compile.json"}
    :routes [(fn [req] {:status 200 :body "test"})]}))