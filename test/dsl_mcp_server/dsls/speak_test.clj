(ns dsl-mcp-server.dsls.speak-test
  (:require [clojure.test :refer :all]
            [dsl-mcp-server.dsls.speak :as speak]))

(deftest get-header-test
  (testing "Header generation"
    (let [result (speak/get-header)]
      (is (:success result))
      (is (clojure.string/includes? (:code result) "interface Speaker"))
      (is (clojure.string/includes? (:code result) "public function speak():Void")))))

(deftest compile-to-haxe-test
  (testing "Valid input"
    (let [input "Bob says Hello Teenage America"
          result (speak/compile-to-haxe input)]
      (is (:success result))
      (is (clojure.string/includes? (:code result) "class Bob"))
      (is (clojure.string/includes? (:code result) "implements Speaker"))
      (is (clojure.string/includes? (:code result) "trace(\"Hello Teenage America\")"))
      (is (clojure.string/includes? (:code result) "public function new()"))))
  (testing "Invalid input"
    (let [input "Invalid input"
          result (speak/compile-to-haxe input)]
      (is (not (:success result)))
      (is (:error result)))))

(deftest eyeball-haxe-test
  (testing "Valid code"
    (let [code "class TestSpeaker implements Speaker {
                 public function new() {}
                 public function speak():Void {
                   trace('Hello');
                 }
               }"
          result (speak/eyeball-haxe code)]
      (is (= "seems ok" (:state result)))
      (is (empty? (:issues result)))))

  (testing "Missing interface implementation"
    (let [code "class TestSpeaker {
                 public function new() {}
                 public function speak():Void {
                   trace('Hello');
                 }
               }"
          result (speak/eyeball-haxe code)]
      (is (= "issues" (:state result)))
      (is (= ["Class does not implement Speaker interface"] (:issues result)))))

  (testing "Missing constructor"
    (let [code "class TestSpeaker implements Speaker {
                 public function speak():Void {
                   trace('Hello');
                 }
               }"
          result (speak/eyeball-haxe code)]
      (is (= "issues" (:state result)))
      (is (= ["Missing public constructor"] (:issues result)))))

  (testing "Missing speak method"
    (let [code "class TestSpeaker implements Speaker {
                 public function new() {}
               }"
          result (speak/eyeball-haxe code)]
      (is (= "issues" (:state result)))
      (is (= ["Missing speak() method" "Missing trace statement in speak() method"] (:issues result)))))

  (testing "Missing trace statement"
    (let [code "class TestSpeaker implements Speaker {
                 public function new() {}
                 public function speak():Void {}
               }"
          result (speak/eyeball-haxe code)]
      (is (= "issues" (:state result)))
      (is (= ["Missing trace statement in speak() method"] (:issues result)))))

  (testing "Multiple issues"
    (let [code "class TestSpeaker {}"
          result (speak/eyeball-haxe code)]
      (is (= "issues" (:state result)))
      (is (= 4 (count (:issues result))))
      (is (some #{"Class does not implement Speaker interface"} (:issues result)))
      (is (some #{"Missing public constructor"} (:issues result)))
      (is (some #{"Missing speak() method"} (:issues result)))
      (is (some #{"Missing trace statement in speak() method"} (:issues result)))))) 