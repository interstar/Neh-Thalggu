(ns neh-thalggu.plugin-loader-test
  (:require [clojure.test :refer :all]
            [neh-thalggu.plugin-loader :as loader :refer [tag-path]]
            [neh-thalggu.schema :as schema]
            [clojure.java.io :as io]
            [malli.core :as m]))

(def test-plugin-dir "plugins")

(deftest load-plugin-test
  (testing "Loading a plugin"
    (let [plugin (loader/load-plugin test-plugin-dir "speak")]
      (is (map? plugin))
      (is (= "speak" (:name plugin)))
      (is (= "A simple DSL for generating things that say Hello" (:description plugin))))))

(deftest load-plugins-test
  (testing "Loading all plugins into registry"
    (let [registry (loader/load-plugins test-plugin-dir)]
      (is (map? registry))
      (is (contains? registry :dsls))
      (is (contains? registry :prompts))
      (is (contains? registry :routes))
      (is (contains? (:dsls registry) "speak"))
      (is (m/validate schema/registry-schema registry)))))

(deftest plugin-schema-test
  (testing "Plugin structure matches schema"
    (let [plugin (loader/load-plugin test-plugin-dir "speak")]
      (is (m/validate schema/plugin-schema plugin)))))

(deftest plugin-function-results-test
  (testing "Plugin functions return correct schema"
    (let [plugin (loader/load-plugin test-plugin-dir "speak")
          compile-fn (get-in plugin [:targets "java" :compile-fn])
          header-fn (get-in plugin [:targets "java" :header-fn])
          eyeball-fn (get-in plugin [:targets "java" :eyeball-fn])]
      (is (m/validate schema/compile-result-schema (compile-fn "test input")))
      (is (m/validate schema/header-result-schema (header-fn)))
      (is (m/validate schema/eyeball-result-schema (eyeball-fn "test code"))))))




(def sample-hiccup
  [:div {:id "main"}
    [:header {:class "top"} "Title"]
    [:section
      [:ul
        [:li "One"]
        [:li "Two"]]]
    [:footer "End"]])

(deftest tag-path-test
  (testing "basic tag path lookups"
    (println "=======  tag-path")
    (is (= (tag-path sample-hiccup [:div :header])
           [:header {:class "top"} "Title"]))

    (is (= (tag-path sample-hiccup [:div :section :ul :li])
           [:li "One"]))

    (is (= (tag-path sample-hiccup [:div :footer])
           [:footer "End"])))

  (testing "nonexistent tag path returns nil"
    (is (nil? (tag-path sample-hiccup [:nav])))

    (is (nil? (tag-path sample-hiccup [:section :ul :span]))))

  (testing "empty path returns root node"
    (is (= (tag-path sample-hiccup [])
           sample-hiccup)))

  (testing "backtracking through multiple branches"
    (let [tree [:tag1 
                [:tag2 "blah"] 
                [:tag2 
                 [:tag3 "foo"]]]]
      (is (= (tag-path tree [:tag1 :tag2 :tag3])
             [:tag3 "foo"])))))
