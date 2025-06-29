(ns neh-thalggu.schema
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

(def plugin-metadata-schema
  [:map
   [:name string?]
   [:type [:enum :native :java-jar :clojure-jar]]
   [:description string?]
   [:version string?]
   [:author string?]
   [:jar-file {:optional true} string?]])

(def plugin-schema
  [:map
   [:metadata plugin-metadata-schema]
   [:grammar
    [:map
     [:rules [:map-of string? string?]]
     [:start string?]]]
   [:targets
    [:map-of
     string?
     [:map
      [:description string?]
      [:prompts
       [:map
        [:compile string?]
        [:header string?]
        [:eyeball string?]]]
      [:compile-fn fn?]
      [:header-fn fn?]
      [:eyeball-fn fn?]]]]])

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
         [:prompts
          [:map
           [:compile string?]
           [:header string?]
           [:eyeball string?]]]]]]
      ]
    ]]
   [:routes
    [:sequential fn?]]
   [:prompt-routes
    [:sequential fn?]]])

;; Helper functions for validation
(defn validate-plugin-metadata
  "Validates plugin metadata against the schema"
  [metadata]
  (let [result (m/validate plugin-metadata-schema metadata)]
    (if (true? result)
      {:valid true}
      {:valid false
       :errors (me/humanize (m/explain plugin-metadata-schema metadata))})))

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
  ;; Validate plugin metadata
  (validate-plugin-metadata
   {:name "test-plugin"
    :type :native
    :description "A test plugin"
    :version "1.0.0"
    :author "Test Author"})

  ;; Validate full plugin
  (validate-plugin
   {:metadata {:name "test-plugin"
               :type :native
               :description "A test plugin"
               :version "1.0.0"
               :author "Test Author"}
    :targets {"haxe" {:description "Haxe target"
                      :compile-fn (fn [x] x)
                      :header-fn (fn [] "header")
                      :eyeball-fn (fn [x] x)}}
    :grammar {:rules {"S" "..."} :start "S"}})

  ;; Validate registry
  (validate-registry
   {:dsls {"test-dsl" {:targets {"haxe" {:description "Haxe target"
                                         :compile-fn (fn [x] x)
                                         :header-fn (fn [] "header")
                                         :eyeball-fn (fn [x] x)}}}}
    :prompt-routes [(fn [req] {:status 200 :body "test"})]
    :routes [(fn [req] {:status 200 :body "test"})]}))


