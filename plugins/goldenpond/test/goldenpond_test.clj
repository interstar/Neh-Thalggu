(ns goldenpond.test.goldenpond-test
  (:require [clojure.test :refer :all]
            [goldenpond.dsl :refer [get-plugin]]
            [instaparse.core :as insta]
            [instaparse.failure :as instafail]
            [dsl-mcp-server.plugin-loader :as loader]
            [clojure.data.json :as json]))

(deftest test-goldenpond-plugin
  (testing "Plugin structure"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")]
      (is (= "goldenpond" (:name plugin)))
      (is (contains? (:targets plugin) "summary"))
      (is (fn? (get-in plugin [:targets "summary" :compile-fn])))
      (is (fn? (get-in plugin [:targets "summary" :header-fn])))
      (is (fn? (get-in plugin [:targets "summary" :eyeball-fn]))))))

(deftest test-goldenpond-compilation
  (testing "Basic compilation"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          compile-fn (get-in plugin [:targets "summary" :compile-fn])
          result (compile-fn "48 Major 120\n71,76,72,75\n0 100 5/8 c 1")]
      (is (map? result))
      (is (contains? result :success))
      (is (contains? result :code)))))

(deftest test-goldenpond-failing-case
  (testing "Specific failing case"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          compile-fn (get-in plugin [:targets "summary" :compile-fn])
          failing-input "48 Major 120\n71,76,72,75,71,76,72,75i\n0 100 5/8 c 1\n1 100 1.>. 2\n2 100 4%8 1 4\n"
          result (compile-fn failing-input)]
      (println "Failing case result:" result)
      (is (map? result))
      (is (contains? result :success))
      (if (:success result)
        (do
          (is (contains? result :code))
          (println "Success! Generated code:" (:code result)))
        (do
          (is (contains? result :error))
          (println "Error occurred:" (:error result))
          (when (:error result)
            (let [err (:error result)]
              (try
                (let [failure (instaparse.core/get-failure err)]
                  (println "Instaparse failure details:" failure))
                (catch Exception e
                  (println "Could not extract Instaparse failure details:" (.getMessage e))))))
          (when (:notes result)
            (println "Notes:" (:notes result)))
          (when (:warning result)
            (println "Warning:" (:warning result))))))))


(deftest test-goldenpond-header
  (testing "Header generation"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          header-fn (get-in plugin [:targets "summary" :header-fn])
          result (header-fn)]
      (is (map? result))
      (is (contains? result :success))
      (is (contains? result :code))
      (is (string? (:code result))))))

(deftest test-goldenpond-eyeball
  (testing "Eyeball function"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          eyeball-fn (get-in plugin [:targets "summary" :eyeball-fn])
          result (eyeball-fn "GoldenData summary with root note and chord information")]
      (is (map? result))
      (is (contains? result :status))
      (is (contains? result :issues)))))

(deftest test-goldenpond-grammar
  (testing "Grammar parsing"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          grammar (:grammar plugin)]
      (is (map? grammar))
      (is (contains? grammar :rules))
      (is (contains? grammar :start))
      (is (= "Music" (:start grammar))))))

(deftest test-goldenpond-description
  (testing "Plugin description"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")]
      (is (string? (:description plugin)))
      (is (re-find #"musical" (:description plugin)))
      (is (re-find #"GoldenPond" (:description plugin))))))

(deftest test-goldenpond-example
  (testing "Test that the example input parses correctly"
    (let [parser goldenpond.dsl/parser
          input "48 Major 120
71,76,72,75,71,76,72,75i
0 100 5/8 c 1
1 100 1.>. 2
2 100 4%8 1 4"
          parse-result (insta/parse parser input)]
      (if (insta/failure? parse-result)
        (do
          (println "Instaparse failed, but why? :")
          (println (with-out-str (instafail/pprint-failure parse-result)))
          (is false "Parsing should succeed"))
        (do
          (println "Parse succeeded:")
          (println parse-result)
          (is true "Parsing succeeded as expected"))))))

(deftest test-goldenpond-json-target
  (testing "JSON target compilation"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          compile-fn (get-in plugin [:targets "json" :compile-fn])
          result (compile-fn "48 Minor 120\n71,76,72,75,71,76,72,75i\n0 100 5/8 c 1\n1 100 1.>. 2\n2 100 4%8 1 4")]
      (println "JSON target result:" result)
      (is (map? result))
      (is (contains? result :success))
      (if (not (:success result))
        (do
          (is (contains? result :error))
          (println "Error occurred:" (:error result)))
        (do
          (is (contains? result :code))
          (let [json-string (first (:code result))]
            (println "Generated JSON:" json-string)
            ;; Parse the JSON to verify structure
            (try
              (let [json-data (json/read-str json-string)]
                (is (contains? json-data "meta"))
                (is (contains? json-data "lines"))
                (is (contains? (get json-data "lines") "0"))
                (is (contains? (get json-data "lines") "1"))
                (is (contains? (get json-data "lines") "2"))
                ;; Check that notes have required fields
                (let [notes-0 (get-in json-data ["lines" "0"])]
                  (when (seq notes-0)
                    (let [first-note (first notes-0)]
                      (is (contains? first-note "midinote"))
                      (is (contains? first-note "start"))
                      (is (contains? first-note "duration"))
                      (is (contains? first-note "velocity")))))
                (println "JSON structure validation passed"))
              (catch Exception e
                (println "JSON parsing failed:" (.getMessage e))
                (is false "JSON should be valid")))))))))

(deftest test-goldenpond-json-header
  (testing "JSON target header generation"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          header-fn (get-in plugin [:targets "json" :header-fn])
          result (header-fn)]
      (is (map? result))
      (is (contains? result :success))
      (is (contains? result :code))
      (is (string? (:code result)))
      (is (re-find #"JSON" (:code result))))))

(deftest test-goldenpond-json-eyeball
  (testing "JSON target eyeball function"
    (let [plugin (loader/load-plugin "plugins" "goldenpond")
          eyeball-fn (get-in plugin [:targets "json" :eyeball-fn])
          valid-json "{\"meta\":{\"bpm\":120},\"lines\":{\"0\":[{\"midinote\":60,\"start\":0.0,\"duration\":0.5,\"velocity\":100}]}}"
          result (eyeball-fn valid-json)]
      (is (map? result))
      (is (contains? result :status))
      (is (contains? result :issues))
      (is (= "seems ok" (:status result))))))

(run-tests)
