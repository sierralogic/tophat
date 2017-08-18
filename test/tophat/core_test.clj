(ns tophat.core-test
  (:require [clojure.test :refer :all]
            [tophat.core :refer :all]
            [tophat.helper :refer [run-labelled-ea-tests run-ea-tests run-generative-tests generative-testing] :as helper]
            [yaml.core :as yaml]))

(def ok-body "meh, but ok")
(def not-ok-body "meh, but not ok")

(deftest test-simple-ok
  (testing "test-simple-ok"
    (is (ok? (ok ok-body)))))

(deftest test-simple-response-status
  (testing "simple http response status"
    (status-of? (response not-found-status) not-found-status)))

(deftest test-simple-response-body
  (testing "simple http response body"
    (is (= (<-body (response not-found-status ok-body)) ok-body))))

;; body->map : tests http request body conversion to clojure map using http request headers/content-type mimetype

(def test-xml-s "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<note>
  <to>Tove</to>
  <to>Tanya</to>
  <from trusted=\"true\">Jani</from>
  <heading>Reminder</heading>
  <body>Don't forget me this weekend!</body>
</note>")
(def test-xml-map (->cxml test-xml-s))
(def test-xml-map-s (->xml-str test-xml-map))

(def test-req-body-map {:foo "bar"
                        :ans 42
                        :fruits ["apple" "pear"]
                        :tau 6.26
                        :dog {:name "Bailey"}})

(def test-edn-s (pprint-string test-req-body-map))
(def test-json-s (->json-str test-req-body-map))
(def test-transit-s (->transit test-req-body-map))
(def test-yaml-s (yaml/generate-string test-req-body-map))

(def req-body-edn {:headers {"content-type" mimetype-edn} :body (str test-req-body-map)})
(def req-body-edn-stream (update req-body-edn :body ->input-stream))
(def req-body-json {:headers {"content-type" mimetype-json} :body test-json-s})
(def req-body-json-stream (update req-body-json :body ->input-stream))
(def req-body-transit {:headers {"content-type" "application/transit+json"} :body test-transit-s})
(def req-body-transit-stream (update req-body-transit :body ->input-stream))
(def req-body-xml {:headers {"content-type" mimetype-xml} :body test-xml-s})
(def req-body-xml-stream (update req-body-xml :body ->input-stream))
(def req-body-yaml {:headers {"content-type" mimetype-yaml} :body test-yaml-s})
(def req-body-yaml-stream (update req-body-yaml :body ->input-stream))

(def body->map-eas [["edn req body->map" [req-body-edn] test-req-body-map]
                    ["edn stream req body->map" [req-body-edn-stream] test-req-body-map]
                    ["json req body->map" [req-body-json] test-req-body-map]
                    ["json stream req body->map" [req-body-json-stream] test-req-body-map]
                    ["transit req body->map" [req-body-transit] test-req-body-map]
                    ["transit stream req body->map" [req-body-transit-stream] test-req-body-map]
                    ["xml req body->map" [req-body-xml] test-xml-map]
                    ["xml stream req body->map" [req-body-xml-stream] test-xml-map]
                    ["yaml req body->map" [req-body-yaml] test-req-body-map]
                    ["yaml stream req body->map" [req-body-yaml-stream] test-req-body-map]])

(deftest testing-req-body->map-eas
  (run-labelled-ea-tests body->map body->map-eas))

;; body->text : tests the http response body being transformed to text based on http request headers/accept mimetype

(def test-edn-body {:body test-req-body-map})
(defn as-body [b ct] {:body b
                      :headers {"Content-Type" ct}})

(def body->text-eas [["edn res body->text" [edn-accept test-edn-body] (as-body test-edn-s mimetype-edn)]
                     ["json res body->text" [json-accept test-edn-body] (as-body test-json-s mimetype-json)]
                     ["transit res body->text" [transit-accept test-edn-body] (as-body test-transit-s mimetype-transit-json)]
                     ["xml res body->text" [xml-accept {:body test-xml-map}] (as-body test-xml-map-s mimetype-xml)]
                     ["yaml res body->text" [yaml-accept test-edn-body] (as-body test-yaml-s mimetype-yaml)]])

(deftest testing-res-body->text
  (run-labelled-ea-tests body->text body->text-eas))

;; if/when ok macros : tests the if/when ok let macros


(deftest testing-if-when-ok-macros
  (testing "simple if ok macro is ok"
    (is (= (if-let-ok [r (ok ok-body)]
                      :ok
                      :not-ok)
           :ok)))
  (testing "simple if ok macro is not ok"
    (is (= (if-let-ok [r (not-found not-ok-body)]
                      :ok
                      :not-ok)
           :not-ok)))
  (testing "if ok macro is ok, checking let"
    (is (= (if-let-ok [r (ok ok-body)]
                      (:body r)
                      :not-ok)
           ok-body)))
  (testing "if ok macro is not ok, checking let"
    (is (= (if-let-ok [r (internal-server-error not-ok-body)]
                      :ok
                      (:body r))
           not-ok-body)))
  (testing "simple when ok macro is ok"
    (is (= (when-let-ok [r (ok ok-body)]
                        :ok)
           :ok)))
  (testing "simple when ok macro is not ok"
    (is (= (when-let-ok [r (not-found not-ok-body)]
                        :ok)
           nil)))
  (testing "when ok macro is ok, checking let"
    (is (= (when-let-ok [r (ok ok-body)]
                        (:body r))
           ok-body)))
  (testing "when ok macro is not ok, checking let"
    (is (= (when-let-ok [r (internal-server-error not-ok-body)]
                        :ok)
           nil)))
  (testing "simple when not ok macro is ok"
    (is (= (when-let-not-ok [r (ok ok-body)]
                            :ok)
           nil)))
  (testing "simple when not ok macro is not ok"
    (is (= (when-let-not-ok [r (not-found not-ok-body)]
                            :not-ok)
           :not-ok)))
  (testing "when not ok macro is ok, checking let"
    (is (= (when-let-not-ok [r (ok ok-body)]
                            (:body r))
           nil)))
  (testing "when not ok macro is not ok, checking let"
    (is (= (when-let-not-ok [r (internal-server-error not-ok-body)]
                            (:body r))
           not-ok-body))))

;; lifting

(defn force-nil [& _] nil)
(def nil-result-body "Nil is the result body.")
(defn force-exception [& args] (throw (Exception. (str args))))

(deftest testing-lifting
  (testing "default lift"
    (is (= (lift str "meh") (ok "meh")))
    (is (= (lift * 2 3) (ok 6))))
  (testing "default nil response status code"
    (is (= (<-status (lift force-nil)) not-found-status)))
  (testing "default exception status code"
    (is (= (<-status (lift (fn [] (throw (Exception. "forcex"))))) internal-server-error-status)))
  (testing "custom non-nil result status"
    (is (= (<-status (lift-custom {:non-nil-response-status created-status} str "meh"))
           created-status)))
  (testing "custom nil result status"
    (is (= (<-status (lift-custom {:nil-response-status bad-request-status} force-nil))
           bad-request-status)))
  (testing "custom non-nil result handler function"
    (is (= (lift-custom {:result-handler-f (fn [r & _] (str "meh::" r))} str "meh")
           (ok "meh::meh")))
    (is (= (lift-custom {:result-handler-f (fn [r & _] (str "meh::" r))} * 2 3)
           (ok "meh::6"))))
  (testing "custom nil response body"
    (is (= (lift-custom {:nil-response-body nil-result-body} force-nil)
           (not-found nil-result-body)))
    (is (= (lift-custom {:nil-result-handler-f (fn [& _] "should be ignored and :nil-response-body used")
                         :nil-response-body nil-result-body}
                        force-nil)
           (not-found nil-result-body))))
  (testing "custom nil result handler function"
    (is (= (lift-custom {:nil-result-handler-f (fn [& _] nil-result-body)} force-nil)
           (not-found nil-result-body)))
    (is (= (lift-custom {:nil-result-handler-f (fn [os f args] {:args args})} force-nil)
           (not-found {:args []})))
    (is (= (lift-custom {:nil-result-handler-f (fn [os f args] {:args args})} force-nil :x :y)
           (not-found {:args [:x :y]}))))
  (testing "custom exception status"
    (is (= (<-status (lift-custom {:exception-status bad-gateway-status} force-exception "meh"))
           bad-gateway-status)))
  (testing "custom exception result handler function"
    (is (= (lift-custom {:exception-status accepted-status
                         :exception-body-f (fn [e options f args] args)} force-exception :x :y)
           (accepted [:x :y])))))

(def lift+ (partial lift +))
(def lift* (partial lift *))
(def lift- (partial lift -))
(def lift-div (partial lift /))
(defn lift-nil [& _] (bad-gateway nil))

(deftest testing-ok-thread-macros
  (testing "simple ok-> threads"
    (is (= (<-body (ok-> 3 (lift+ 2)))
           5))
    (is (= (<-body (ok-> 3 (lift+ 2) (lift* 4) (lift+ 13)))
           33))
    (is (= (<-body (ok-> 3 (+ 2)))
           5))
    (is (= (ok-> 3 (+ 2) (* 4) (+ 13))
           33)))
  (testing "simple ok-> threads that fail"
    (is (= (<-status (ok-> 3 (lift+ 4 2) (force-nil) (lift- 3)))
           not-found-status))
    (is (= (<-status (ok-> 3 (lift-div 0)))
           internal-server-error-status))))

(deftest testing-some-ok-thread-macros
  (testing "simple some-ok-> threads"
    (is (= (<-body (some-ok-> 3 (lift+ 2)))
           5))
    (is (= (<-body (some-ok-> 3 (lift+ 2) (lift* 4) (lift+ 13)))
           33))
    (is (= (<-body (some-ok-> 3 (+ 2)))
           5))
    (is (= (some-ok-> 3 (+ 2) (* 4) (+ 13))
           33)))
  (testing "simple some-ok-> threads that fail"
    (is (= (<-status (some-ok-> 3 (lift+ 4) (lift-nil) (lift+ 33)))
           bad-gateway-status))
    (is (= (<-status (some-ok-> 3 (lift-div 0)))
           internal-server-error-status))))

(deftest testing-ok-last-thread-macros
  (testing "simple ok->> threads"
    (is (= (<-body (ok->> 3 (lift+ 2)))
           5))
    (is (= (<-body (ok->> 3 (lift+ 2) (lift* 4) (lift+ 13)))
           33))
    (is (= (<-body (ok->> 3 (+ 2)))
           5))
    (is (= (ok->> 3 (+ 2) (* 4) (+ 13))
           33)))
  (testing "simple ok->> threads that fail"
    (is (= (<-status (ok->> 3 (lift+ 4 2) (force-nil) (lift- 3)))
           not-found-status))
    (is (= (<-status (ok->> 0 (lift-div 3)))
           internal-server-error-status))))

(deftest testing-some-ok-last-thread-macros
  (testing "simple some-ok->> threads"
    (is (= (<-body (some-ok->> 3 (lift+ 2)))
           5))
    (is (= (<-body (some-ok->> 3 (lift+ 2) (lift* 4) (lift+ 13)))
           33))
    (is (= (<-body (some-ok->> 3 (+ 2)))
           5))
    (is (= (some-ok->> 3 (+ 2) (* 4) (+ 13))
           33)))
  (testing "simple some-ok->> threads that fail"
    (is (= (<-status (some-ok->> 3 (lift+ 4) (lift-nil) (lift+ 33)))
           bad-gateway-status))
    (is (= (<-status (some-ok->> 0 (lift-div 4)))
           internal-server-error-status))))
