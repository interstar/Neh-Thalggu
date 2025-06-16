(ns speak.test.speak-test
  (:require [clojure.test :refer :all]
            [dsl-mcp-server.plugin-loader :as loader]
            [malli.core :as m]
            [dsl-mcp-server.schema :as schema]
            
            [clojure.string :as string]))

(def plugin-dir "plugins")
(def speak-dsl (loader/load-plugin plugin-dir "speak"))

(println "Testing Speak")
(println "Valid plugin? " (m/validate schema/plugin-schema speak-dsl ))

(defn compile-success? [compile-result] (:success compile-result))
(defn compile-failure? [compile-result] (not (:success compile-result)))

(def compile-fn (-> speak-dsl :targets (get "haxe") :compile-fn))

(deftest schema-check
  (testing "Schema check"
    (is (m/validate schema/plugin-schema speak-dsl))))


(deftest get-header-test
  (testing "Header generation"
    (let [result ((-> speak-dsl :targets (get "haxe") :header-fn))]
      (is (:success result))
      (is (clojure.string/includes? (:code result) "interface ISpeaker"))
      (is (clojure.string/includes? (:code result) "public function speak():Void")))))

(deftest compile-to-haxe-test
  (testing "Valid input"
    (let [input "Bob says Hello Teenage America"
          result (compile-fn input)]      
      (is (compile-success? result))
      (is (clojure.string/includes? (:code result) "class Bob"))
      (is (clojure.string/includes? (:code result) "implements ISpeaker"))
      (is (clojure.string/includes? (:code result) "public function new()"))))
)

(deftest failing-compile-test
  (testing "Invalid input to test fail case"
    
    (let [input "Invalid input to test fail case"
          result (compile-fn input)]
      (is (compile-failure? result)))))

(deftest eyeball-haxe-test
  (testing "Valid code"
    (let [code "class TestSpeaker implements ISpeaker {
                 public function new() {}
                 public function speak():Void {
                   trace('Hello');
                 }
               }"
          result ((-> speak-dsl :targets (get "haxe") :eyeball-fn) code)]
      (is (= "seems ok" (:status result)))
      (is (empty? (:issues result)))))

  (testing "Multiple issues"
    (let [code "class TestSpeaker {}"
          result ((-> speak-dsl :targets (get "haxe") :eyeball-fn) code)]
      (is (= "issues" (:status result)))
      (is (= 3 (count (:issues result))))
      (is (some #{"Class does not implement ISpeaker interface"} (:issues result)))
      (is (some #{"Missing public constructor"} (:issues result)))
      (is (some #{"Missing speak() method"} (:issues result)))
      ))) 
