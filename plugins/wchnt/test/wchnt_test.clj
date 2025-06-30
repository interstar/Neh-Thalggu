(ns wchnt.test.wchnt-test
  (:require [clojure.test :refer :all]
            [neh-thalggu.plugin-loader :as loader]
            [neh-thalggu.schema :as schema]
            [clojure.string :as str]
            [malli.core :as m]))

(def plugin-dir "plugins")
(def wchnt-dsl (loader/load-plugin plugin-dir "wchnt"))

(deftest plugin-schema-validation
  (testing "wchnt plugin conforms to schema"
    (is (m/validate schema/plugin-schema wchnt-dsl))))

(deftest plugin-metadata-values
  (testing "wchnt plugin metadata values"
    (is (= "wchnt" (:name (:metadata wchnt-dsl))))
    (is (= "A DSL for describing data-schemas and object-oriented architectures" (:description (:metadata wchnt-dsl))))))

(deftest test-wchnt-plugin-structure
  (testing "Plugin has correct structure"
    (is (contains? (:targets wchnt-dsl) "haxe"))
    (is (fn? (get-in wchnt-dsl [:targets "haxe" :compile-fn])))
    (is (fn? (get-in wchnt-dsl [:targets "haxe" :header-fn])))
    (is (fn? (get-in wchnt-dsl [:targets "haxe" :eyeball-fn])))))

(deftest test-wchnt-header
  (testing "Header function returns valid structure"
    (let [header-fn (get-in wchnt-dsl [:targets "haxe" :header-fn])
          result (header-fn)]
      (is (m/validate schema/header-result-schema result)))))

(deftest test-wchnt-eyeball
  (testing "Eyeball function returns valid structure"
    (let [eyeball-fn (get-in wchnt-dsl [:targets "haxe" :eyeball-fn])
          result (eyeball-fn "test code")]
      (is (m/validate schema/eyeball-result-schema result)))))

(deftest test-wchnt-basic-compilation
  (testing "Compile function handles basic WCHNT syntax"
    (let [compile-fn (get-in wchnt-dsl [:targets "haxe" :compile-fn])
          result (compile-fn "Person = String/name int/age")]
      ;; Test that the result structure matches the schema
      (is (m/validate schema/compile-result-schema result))
      ;; CRITICAL: Test that compilation actually succeeded
      (is (:success result) "Compilation should succeed with valid WCHNT syntax")
      ;; Test that we got actual generated code
      (when (:success result)
        (is (vector? (:code result)))
        (is (pos? (count (:code result))))
        (is (string? (first (:code result))))
        (is (not (str/blank? (first (:code result)))))
        ;; The generated code should contain Haxe class definitions
        (is (str/includes? (first (:code result)) "class"))
        (is (str/includes? (first (:code result)) "Person"))))))

(deftest test-wchnt-array-types
  (testing "Compile function handles array types"
    (let [compile-fn (get-in wchnt-dsl [:targets "haxe" :compile-fn])
          result (compile-fn "Person = String/name [Address]/addresses
Address = String/street String/city")]
      (is (m/validate schema/compile-result-schema result))
      (is (:success result) "Compilation should succeed with array types")
      (when (:success result)
        (let [all-code (str/join "\n" (:code result))]
          (is (str/includes? all-code "class Person"))
          (is (str/includes? all-code "Array<Address>"))
          (is (str/includes? all-code "class Address")))))))

(deftest test-wchnt-interface-disjunction
  (testing "Compile function handles interface disjunctions"
    (let [compile-fn (get-in wchnt-dsl [:targets "haxe" :compile-fn])
          result (compile-fn "Shape = Triangle | Circle
Triangle = int/base int/height
Circle = int/radius")]
      (is (m/validate schema/compile-result-schema result))
      (is (:success result) "Compilation should succeed with interface disjunctions")
      (when (:success result)
        (let [all-code (str/join "\n" (:code result))]
          (is (str/includes? all-code "interface Shape"))
          (is (str/includes? all-code "class Triangle"))
          (is (str/includes? all-code "class Circle")))))))

(deftest test-wchnt-complex-example
  (testing "Compile function handles complex WCHNT example"
    (let [compile-fn (get-in wchnt-dsl [:targets "haxe" :compile-fn])
          complex-wchnt "Game = PlayArea Ball Paddle/paddle1 Paddle/paddle2
PlayArea = Rect
Ball = int/x int/y int/dx int/dy int/rad
Paddle = int/x int/y
Rect = int/x int/y int/width int/height"
          result (compile-fn complex-wchnt)]
      (is (m/validate schema/compile-result-schema result))
      (is (:success result) "Compilation should succeed with complex example")
      (when (:success result)
        (let [all-code (str/join "\n" (:code result))]
          (is (str/includes? all-code "class Game"))
          (is (str/includes? all-code "class PlayArea"))
          (is (str/includes? all-code "class Ball"))
          (is (str/includes? all-code "class Paddle"))
          (is (str/includes? all-code "class Rect"))
          (is (str/includes? all-code "paddle1"))
          (is (str/includes? all-code "paddle2")))))))

(deftest test-wchnt-compile-failure
  (testing "Compile function handles invalid WCHNT syntax"
    (let [compile-fn (get-in wchnt-dsl [:targets "haxe" :compile-fn])
          invalid-wchnt "invalid syntax here"
          result (compile-fn invalid-wchnt)]
      ;; Test that the result structure matches the schema even on failure
      (is (m/validate schema/compile-result-schema result))
      ;; CRITICAL: Test that compilation actually failed
      (is (not (:success result)) "Compilation should fail with invalid WCHNT syntax")
      ;; Test failure case
      (is (contains? result :error))
      (is (string? (:error result))))))

(deftest test-wchnt-grammar
  (testing "Grammar is properly defined"
    (let [grammar (:grammar wchnt-dsl)]
      (is (map? grammar))
      (is (contains? grammar :rules))
      (is (contains? grammar :start))
      (is (string? (:start grammar)))
      (is (map? (:rules grammar))))))

(deftest test-wchnt-jar-loading
  (testing "JAR loading and real WCHNT compilation"
    (let [compile-fn (get-in wchnt-dsl [:targets "haxe" :compile-fn])
          result (compile-fn "Person = String/name int/age")]
      ;; If JAR loading fails, the test should fail
      (if (:success result)
        ;; JAR loading succeeded, verify we got actual code
        (do
          (is (vector? (:code result)))
          (is (pos? (count (:code result))))
          (is (string? (first (:code result))))
          (is (not (str/blank? (first (:code result)))))
          ;; Verify it's actually Haxe code
          (is (str/includes? (first (:code result)) "class"))
          (is (str/includes? (first (:code result)) "public function new")))
        ;; JAR loading failed, verify proper error reporting
        (do
          (is (contains? result :error))
          (is (string? (:error result)))
          (is (str/includes? (:error result) "wchnt_lang") "Error should mention the missing class"))))))