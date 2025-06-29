(ns neh-thalggu.makedsl-foreign-test
  (:require [clojure.test :refer :all]
            [neh-thalggu.plugin-loader :as loader]
            [neh-thalggu.schema :as schema]
            [malli.core :as m]))

(def test-plugin-dir "plugins")

(deftest makedsl-foreign-target-exists-test
  (testing "makedsl plugin has foreign target"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")]
      (is (contains? (:targets plugin) "foreign"))
      (is (= "Generate foreign JAR wrapper DSL plugin files" 
             (get-in plugin [:targets "foreign" :description]))))))

(deftest makedsl-foreign-compile-fn-test
  (testing "foreign target compile function works with valid input"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          compile-fn (get-in plugin [:targets "foreign" :compile-fn])
          input "Name: testdsl
Description: A test DSL for JAR wrapper
S = Command SPACE Parameter
Command = #'[A-Za-z]+'
Parameter = #'[A-Za-z0-9]+'
<SPACE> = #'\\s+'"
          result (compile-fn input)]
      (is (:success result))
      (is (= 3 (count (:code result)))) ; Should return 3 files: dsl.clj, test.clj, README.md
      (is (string? (first (:code result))))
      (is (string? (second (:code result))))
      (is (string? (nth (:code result) 2))))))

(deftest makedsl-foreign-compile-fn-invalid-input-test
  (testing "foreign target compile function handles invalid input"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          compile-fn (get-in plugin [:targets "foreign" :compile-fn])
          input "Invalid input without Name: or Description:"
          result (compile-fn input)]
      (is (not (:success result)))
      (is (string? (:error result)))
      (is (= 3 (count (:code result)))) ; Should still return 3 empty strings
      (is (= "" (first (:code result))))
      (is (= "" (second (:code result))))
      (is (= "" (nth (:code result) 2))))))

(deftest makedsl-foreign-header-fn-test
  (testing "foreign target header function returns valid structure"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          header-fn (get-in plugin [:targets "foreign" :header-fn])
          result (header-fn)]
      (is (:success result))
      (is (string? (:code result)))
      (is (string? (:notes result)))
      (is (string? (:warning result)))
      (is (m/validate schema/header-result-schema result))
      (is (re-find #"foreign JAR wrapper" (:code result)))
      (is (re-find #"load-java-class" (:code result))))))

(deftest makedsl-foreign-eyeball-fn-test
  (testing "foreign target eyeball function returns valid structure"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          eyeball-fn (get-in plugin [:targets "foreign" :eyeball-fn])
          result (eyeball-fn "test code")]
      (is (contains? #{"seems ok" "issues"} (:status result)))
      (is (vector? (:issues result)))
      (is (string? (:notes result)))
      (is (m/validate schema/eyeball-result-schema result)))))

(deftest makedsl-foreign-generated-template-test
  (testing "foreign target generates valid template structure"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          compile-fn (get-in plugin [:targets "foreign" :compile-fn])
          input "Name: testdsl
Description: A test DSL for JAR wrapper
S = Command SPACE Parameter
Command = #'[A-Za-z]+'
Parameter = #'[A-Za-z0-9]+'
<SPACE> = #'\\s+'"
          result (compile-fn input)
          [dsl-clj test-clj readme-md] (:code result)]
      
      ;; Test dsl.clj template
      (is (re-find #"ns testdsl.dsl" dsl-clj))
      (is (re-find #"defn get-plugin.*tag-path.*load-fns" dsl-clj))
      (is (re-find #"load-java-class" dsl-clj))
      (is (re-find #"TODO.*Replace with actual generated output" dsl-clj))
      
      ;; Test test.clj template
      (is (re-find #"ns testdsl.test" test-clj))
      (is (re-find #"get-plugin identity identity" test-clj))
      (is (re-find #"test-testdsl-plugin-structure" test-clj))
      
      ;; Test README.md template
      (is (re-find #"# testdsl DSL Plugin" readme-md))
      (is (re-find #"foreign JAR wrapper" readme-md))
      (is (re-find #"TODO Items" readme-md))
      (is (re-find #"load-java-class" readme-md)))))

(deftest makedsl-foreign-prompts-test
  (testing "foreign target has all required prompts"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          prompts (get-in plugin [:targets "foreign" :prompts])]
      (is (contains? prompts :compile))
      (is (contains? prompts :header))
      (is (contains? prompts :eyeball))
      (is (string? (:compile prompts)))
      (is (string? (:header prompts)))
      (is (string? (:eyeball prompts)))
      (is (re-find #"foreign JAR wrapper" (:compile prompts)))
      (is (re-find #"load-java-class" (:header prompts)))
      (is (re-find #"JAR loading" (:eyeball prompts))))))

(deftest makedsl-both-targets-exist-test
  (testing "makedsl plugin has both clojure and foreign targets"
    (let [plugin (loader/load-plugin test-plugin-dir "makedsl")
          targets (:targets plugin)]
      (is (contains? targets "clojure"))
      (is (contains? targets "foreign"))
      (is (= "Generate Clojure DSL plugin files" 
             (get-in targets ["clojure" :description])))
      (is (= "Generate foreign JAR wrapper DSL plugin files" 
             (get-in targets ["foreign" :description]))))))