(ns dsl-mcp-server.registry-test
  (:require [clojure.test :refer :all]
            [dsl-mcp-server.registry :as registry]
            [dsl-mcp-server.schema :as schema]
            [malli.core :as m]
            [cheshire.core :as json]
            [dsl-mcp-server.plugin-loader :as loader]
            [clojure.string :as str]))

(def test-plugin-dir "plugins")
(def test-registry (loader/load-plugins test-plugin-dir))

(deftest registry-schema-test
  (testing "Test registry matches schema"
    (is (m/validate schema/registry-schema test-registry))))

(deftest compile-result-schema-test
  (testing "Compile function returns valid schema"
    (let [compile-fn (get-in test-registry [:dsls "speak" :targets "haxe" :compile-fn])
          result (compile-fn "test input")]
      (is (m/validate schema/compile-result-schema result)))))

(deftest header-result-schema-test
  (testing "Header function returns valid schema"
    (let [header-fn (get-in test-registry [:dsls "speak" :targets "haxe" :header-fn])
          result (header-fn)]
      (is (m/validate schema/header-result-schema result)))))

(deftest eyeball-result-schema-test
  (testing "Eyeball function returns valid schema"
    (let [eyeball-fn (get-in test-registry [:dsls "speak" :targets "haxe" :eyeball-fn])
          result (eyeball-fn "test code")]
      (is (m/validate schema/eyeball-result-schema result)))))

(deftest get-tool-descriptions-test
  (testing "Getting tool descriptions"
    (let [descriptions (registry/get-tool-descriptions test-registry)]
      (is (map? descriptions))
      (is (contains? descriptions :tools))
      (is (contains? descriptions :prompts))
      (is (contains? descriptions :resources))
      (let [tools (:tools descriptions)]
        (is (sequential? tools))
        (is (some #(= (:name %) "compile-speak-haxe") tools))
        (is (some #(= (:name %) "header-speak-haxe") tools))))))

(deftest list-prompts-test
  (testing "Listing prompts like the handler"
    (let [prompts (into {}
                        (for [[dsl-name dsl-info] (:dsls test-registry)
                              [target target-info] (:targets dsl-info)
                              [prompt-type prompt-content] (:prompts target-info)]
                          [(str prompt-type "-" dsl-name "-" target) prompt-content]))]
      (is (map? prompts))
      (is (some #(clojure.string/starts-with? % "compile-") (keys prompts)))
      (is (every? string? (vals prompts))))))

(deftest get-prompt-test
  (testing "Getting existing prompt"
    (is (string? (registry/get-prompt test-registry "compile-speak-haxe"))))
  (testing "Getting non-existent prompt"
    (is (nil? (registry/get-prompt test-registry "non-existent-prompt")))))

(deftest get-tool-routes-test
  (testing "Getting tool routes"
    (let [routes (registry/get-tool-routes test-registry)]
      (is (sequential? routes))
      (is (pos? (count routes))))))

(deftest get-prompt-routes-test
  (testing "Getting prompt routes"
    (let [routes (registry/get-prompt-routes test-registry)]
      (is (sequential? routes))
      (is (pos? (count routes))))))




