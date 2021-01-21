(ns sfsim25.rgb-test
  (:refer-clojure :exclude [+ *])
  (:require [clojure.test :refer :all]
            [sfsim25.rgb :refer :all]))

(deftest display-test
  (testing "Display RGB value"
    (is (= "(rgb 2.0 3.0 5.0)" (str (->RGB 2 3 5))))))

(deftest component-test
  (testing "Get components of RGB value"
    (is (= 2.0 (r (->RGB 2 3 5))))
    (is (= 3.0 (g (->RGB 2 3 5))))
    (is (= 5.0 (b (->RGB 2 3 5))))))

(deftest add-test
  (testing "Add RGB values"
    (is (= (->RGB 2 3 5) (+ (->RGB 2 3 5))))
    (is (= (->RGB 5 8 12) (+ (->RGB 2 3 5) (->RGB 3 5 7))))
    (is (= (->RGB 10 15 23) (+ (->RGB 2 3 5) (->RGB 3 5 7) (->RGB 5 7 11))))))

(deftest scale-test
  (testing "Scale an RGB value"
    (is (= (->RGB 4 6 10) (* 2 (->RGB 2 3 5))))))
