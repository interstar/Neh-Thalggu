(ns dsl-mcp-server.dsls.speak-test
  (:require [clojure.test :refer :all]
            [dsl-mcp-server.dsls.speak :as speak]))

(deftest get-header-test
  (testing "Header generation"
    (let [result (speak/get-header)]
      (is (:success result))
      (is (clojure.string/includes? (:haxeCode result) "interface Speaker"))
      (is (clojure.string/includes? (:haxeCode result) "public function speak():Void")))))

(deftest compile-to-haxe-test
  (testing "Valid input"
    (let [input "Bob says Hello Teenage America"
          result (speak/compile-to-haxe input)]
      (is (:success result))
      (is (clojure.string/includes? (:haxeCode result) "class Bob"))
      (is (clojure.string/includes? (:haxeCode result) "implements Speaker"))
      (is (clojure.string/includes? (:haxeCode result) "trace(\"Hello Teenage America\")"))
      (is (clojure.string/includes? (:haxeCode result) "public function new()"))))
  (testing "Invalid input"
    (let [input "Invalid input"
          result (speak/compile-to-haxe input)]
      (is (not (:success result)))
      (is (:error result))))) 