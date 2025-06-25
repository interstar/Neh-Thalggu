(ns speak.test.speak-test
  (:require [clojure.test :refer :all]
            [neh-thalggu.plugin-loader :as loader]
            [malli.core :as m]
            [neh-thalggu.schema :as schema]
            
            [clojure.string :as string]))

(def plugin-dir "plugins")
(def speak-dsl (loader/load-plugin plugin-dir "speak"))

(println "Testing Speak")
(println "Valid plugin? " (m/validate schema/plugin-schema speak-dsl ))

(defn compile-success? [compile-result] (:success compile-result))
(defn compile-failure? [compile-result] (not (:success compile-result)))

(def compile-fn (-> speak-dsl :targets (get "java") :compile-fn))

(deftest schema-check
  (testing "Schema check"
    (is (m/validate schema/plugin-schema speak-dsl))))


(deftest get-header-test
  (testing "Header generation"
    (let [result ((-> speak-dsl :targets (get "java") :header-fn))]
      (is (:success result))
      (is (clojure.string/includes? (:code result) "public interface ISpeaker"))
      (is (clojure.string/includes? (:code result) "void speak()")))))

(deftest compile-to-java-test
  (testing "Valid input"
    (let [input "Bob says Hello Teenage America"
          result (compile-fn input)]      
      (is (compile-success? result))
      (is (clojure.string/includes? (:code result) "public class Bob"))
      (is (clojure.string/includes? (:code result) "implements ISpeaker"))
      (is (clojure.string/includes? (:code result) "public Bob()"))
      (is (clojure.string/includes? (:code result) "public void speak()"))
      (is (clojure.string/includes? (:code result) "System.out.println"))))
)

(deftest failing-compile-test
  (testing "Invalid input to test fail case"
    
    (let [input "Invalid input to test fail case"
          result (compile-fn input)]
      (is (compile-failure? result)))))

(deftest eyeball-java-test
  (testing "Valid code"
    (let [code "public class TestSpeaker implements ISpeaker {
                 public TestSpeaker() {}
                 public void speak() {
                   System.out.println(\"Hello\");
                 }
               }"
          result ((-> speak-dsl :targets (get "java") :eyeball-fn) code)]
      (is (= "seems ok" (:status result)))
      (is (empty? (:issues result)))))

  (testing "Multiple issues"
    (let [code "public class TestSpeaker {}"
          result ((-> speak-dsl :targets (get "java") :eyeball-fn) code)]
      (is (= "issues" (:status result)))
      (is (= 4 (count (:issues result))))
      (is (some #{"Class does not implement ISpeaker interface"} (:issues result)))
      (is (some #{"Missing public constructor"} (:issues result)))
      (is (some #{"Missing speak() method"} (:issues result)))
      (is (some #{"Missing System.out.println statement"} (:issues result)))
      ))) 

;; Python target tests
(deftest get-header-python-test
  (testing "Python header generation"
    (let [result ((-> speak-dsl :targets (get "python") :header-fn))]
      (is (:success result))
      (is (clojure.string/includes? (:code result) "from abc import ABC, abstractmethod"))
      (is (clojure.string/includes? (:code result) "class ISpeaker(ABC)"))
      (is (clojure.string/includes? (:code result) "@abstractmethod"))
      (is (clojure.string/includes? (:code result) "def speak(self)")))))

(deftest compile-to-python-test
  (testing "Valid Python input"
    (let [input "Bob says Hello Teenage America"
          result ((-> speak-dsl :targets (get "python") :compile-fn) input)]      
      (is (compile-success? result))
      (is (clojure.string/includes? (:code result) "class Bob(ISpeaker)"))
      (is (clojure.string/includes? (:code result) "def __init__(self)"))
      (is (clojure.string/includes? (:code result) "def speak(self)"))
      (is (clojure.string/includes? (:code result) "print(")))))

(deftest eyeball-python-test
  (testing "Valid Python code"
    (let [code "class TestSpeaker(ISpeaker):
                 def __init__(self):
                     pass
                 def speak(self):
                     print(\"Hello\")"
          result ((-> speak-dsl :targets (get "python") :eyeball-fn) code)]
      (is (= "seems ok" (:status result)))
      (is (empty? (:issues result)))))

  (testing "Multiple Python issues"
    (let [code "class TestSpeaker:"
          result ((-> speak-dsl :targets (get "python") :eyeball-fn) code)]
      (is (= "issues" (:status result)))
      (is (= 4 (count (:issues result))))
      (is (some #{"Class does not inherit from ISpeaker"} (:issues result)))
      (is (some #{"Missing __init__ constructor"} (:issues result)))
      (is (some #{"Missing speak() method"} (:issues result)))
      (is (some #{"Missing print statement"} (:issues result)))
      ))) 
