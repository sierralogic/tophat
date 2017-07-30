(ns tophat.helper
    (:require [clojure.test :refer :all]))


(defn run-labelled-ea-tests
    "Runs tests on test function test-fn given an expected-actual vector eas.

      The expected-actual list example:

      [ [ \"label for test a\" [:arg1a :arg2a :argna] :expected-value-a ]
        [ \"label for test b\" [:arg1b :arg2b :argnb] :expected-value-b ]
        ...
        [ \"label for test z\" [:arg1z :arg2z :argnz] :expected-value-z ] ]

      Ex.

      If the test-fn is the clojure str function, then a passing eas list might look like:

      [ [ \"testing a\" [\"a\" \"b\" \"c\"] \"abc\"]
        [ \"testing b\" [\"x\" \"y\" \"z\"] \"xyz\"] ]

      (apply str [\"a\" \"b\" \"c\"]) => \"abc\"
      (apply str [\"x\" \"y\" \"z\"]) => \"xyz\"
      "
    [test-fn eas]
    (doall (for [ea eas]
               (testing (str (first ea))
                   (is (= (apply test-fn (second ea)) (nth ea 2)))))))

(defn run-ea-tests
    "Runs tests on test function test-fn given an expected-actual vector eas.

      The expected-actual list example:

      [ [ [:arg1a :arg2a :argna] :expected-value-a ]
        [ [:arg1b :arg2b :argnb] :expected-value-b ]
        ...
        [ [:arg1z :arg2z :argnz] :expected-value-z ] ]

      Ex.

      If the test-fn is the clojure str function, then a passing eas list might look like:

      [ [ [\"a\" \"b\" \"c\"] \"abc\"]
        [ [\"x\" \"y\" \"z\"] \"xyz\"] ]

      (apply str [\"a\" \"b\" \"c\"]) => \"abc\"
      (apply str [\"x\" \"y\" \"z\"]) => \"xyz\"
      "
    [test-fn eas]
    (doall (for [ea eas]
               (is (= (apply test-fn (first ea)) (second ea))))))

(defn run-generative-tests
    "Runs generative test given a context ctx, a function that generates an expected-actual list (see below),
      and the function to be tested test-fn.

      The expected-actual list output from the gen-eas-fn:

      [ [ [:arg1a :arg2a :argna] :expected-value-a ]
        [ [:arg1b :arg2b :argnb] :expected-value-b ]
        ...
        [ [:arg1z :arg2z :argnz] :expected-value-z ] ]

      Ex.

      If the test-fn is the clojure str function, then a passing eas list might look like:

      [ [ [\"a\" \"b\" \"c\"] \"abc\"]
        [ [\"x\" \"y\" \"z\"] \"xyz\"] ]

      (apply str [\"a\" \"b\" \"c\"]) => \"abc\"
      (apply str [\"x\" \"y\" \"z\"]) => \"xyz\"
      "
    [gen-eas-fn test-fn & [ctx]]
    (run-ea-tests test-fn (gen-eas-fn ctx)))

(defn mock-gen-eas
    "Simple mock generation of eas for demonstration."
    [& [ctx]]
    [[["a" "b" "c"] "abc"]
     [["x" "y" "z"] "xyz"]])

(defn gen-eas-str
    "Naive generation of eas for demonstration for str function."
    [& [ctx]]
    (reduce (fn [a x]
                (let [a1 (str x)
                      a2 (str (inc x))
                      a3 (str (+ x 3))
                      exp (str a1 a2 a3)]
                    (conj a [[a1 a2 a3] exp])))
            []
            (range (get ctx :n 10000)))) ;; pulls n number in range from ctx; defaults to 10,000

(defn generative-testing
    [gen-tests-map]
    (doseq [ktm gen-tests-map]
        (let [k (first ktm)
              tm (second ktm)
              {gen-eas-fn :gen-eas-fn
               test-fn :test-fn
               context :context
               label :label
               desc :description} tm]
            (testing (or label (str "testing generative settings " k))
                (run-generative-tests gen-eas-fn test-fn context)))))

(def gen-tests {:str {:gen-eas-fn gen-eas-str
                      :test-fn str
                      :context {:n 20}}
                :str2 {:gen-eas-fn gen-eas-str
                       :test-fn str
                       :context {:n 23}}})