(ns sfsim25.rgb-test
  (:refer-clojure :exclude [+ *])
  (:require [clojure.test :refer :all]
            [sfsim25.rgb :refer :all]))

(deftest display-test
  (testing "Display RGB value"
    (is (= "(rgb 2.0 3.0 5.0)" (str (rgb 2 3 5))))))

(deftest component-test
  (testing "Get components of RGB value"
    (is (= 2.0 (r (rgb 2 3 5))))
    (is (= 3.0 (g (rgb 2 3 5))))
    (is (= 5.0 (b (rgb 2 3 5))))))

(deftest add-test
  (testing "Add two RGB values"
    (is (= (rgb 5 8 12) (+ (rgb 2 3 5) (rgb 3 5 7))))))

(deftest scale-test
  (testing "Scale an RGB value"
    (is (= (rgb 4 6 10) (* 2 (rgb 2 3 5))))))
