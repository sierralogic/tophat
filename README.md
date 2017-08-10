# tophat

Tophat is a Clojure library for handling HTTP request and response documents/maps.

Tophat API documentation may be found <a href="https://sierralogic.github.io/tophat/doc/tophat.core.html" target="_blank">here</a>.

https://circleci.com/gh/sierralogic/tophat.png?circle-token=6459e6f386bf70e85440408259137929828218a3

## Rationale

Tophat began as a utility library that was passed between projects to handle
HTTP request and response documents.

As the library evolved, the request/response maps (aka Ring maps) were not only being used
to handle HTTP-based handling, but also by function for results that can be processed
based on statuses.

## Usage

In `project.clj` dependencies:

```clojure
[tophat "0.1.4"]

```

In code:

```clojure
(ns your-ns
  (:require [tophat.core :as tophat]))

```

## Examples

**NOTE**: Examples require tophat v0.1.4 or later.

```clojure
(require 'tophat.core)
```

### Creating response documents

Creating response documents is easy.

```clojure
(ok "this is ok body") ;; {:status 200, :body "this is ok body"}
(not-found "item not found") ;; {:status 404, :body "item not found"}  
```
Create response document with headers:

```clojure
;; (response $body) (response $status $body) or (response $status $header-map $body)
;; (ok $body) or (ok $header-map $body)

(response {:foo :bar})
;; {:status 200, :body {:foo :bar}}

(response not-found-status {:msg "not found..."})
;; {:status 404, :body {:msg "not found..."}}

(response created-status {"Content-Type" "text/plain"} "entity created")
;; {:status 201, :body "entity created", :headers {"Content-Type" "text/plain"}}

(ok {"Pragma" "no-cache"} "this is ok body") 
;; {:status 200, :body "this is ok body", :headers {"Pragma" "no-cache"}} 

(ok (pragma "no-cache") "this is ok body") 
;; {:status 200, :body "this is ok body", :headers {"Pragma" "no-cache"}} 

(ok (->> (content :text) (pragma "no-cache")) "this is ok body") 
;; {:status 200, :body "this is ok body", :headers {"Content-Type" "text/plain" "Pragma" "no-cache"}} 

(created (content :text) "entity created...")
;; {:status 201, :body "entity created...", :headers {"Content-Type" "text/plain"}}
```

### Creating request documents

```clojure

;; (request $body) or (request $header-map $body)

(request "meh")
;; {:body "meh"}

(request (->> (content :json) (accept :transit)) "{\"foo\" : \"bar\"}")
;; {:body "{\"foo\" : \"bar\"}", :headers {"Content-Type" "application/json", "Accept" "application/transit+json"}}

(request {"Content-Type" "application/json" "Accept" "application/transit+json"} "{\"foo\" : \"bar\"}")
;; {:body "{\"foo\" : \"bar\"}", :headers {"Content-Type" "application/json", "Accept" "application/transit+json"}}
```

### Headers

Both request and response documents have header fields.

Tophat has a variety of convenience function to help deal with headers.

```clojure
(accept :text)
;; {"Accept" "text/plain"}

(content :edn)
;; {"Content-Type" "application/edn"}

(->> (accept :text) (content :edn))
;; {"Accept" "text/plain" "Content-Type" "application/edn"}

(headers {"Accept" "text/plain"})
;; {:headers {"Accept" "text/plain"}}

(headers (accept :text))
;; {:headers {"Accept" "text/plain"}}

(headers (->> (accept :text) (content :json)))
;; {:headers {"Accept" "text/plain", "Content-Type" "application/json"}}

(request (->> (accept :text) (content :edn) (pragma "no-cache")) "{:foo :bar}")
;; {:body "{:foo :bar}", :headers {"Accept" "text/plain", "Content-Type" "application/edn", "Pragma" "no-cache"}}

(ok (->> (content :transit) (pragma "no-cache")) {:foo :bar})
;; {:status 200, :body {:foo :bar}, :headers {"Content-Type" "application/transit+json", "Pragma" "no-cache"}}
```
### Handling documents

Checking status of documents:

```clojure
(ok? (ok "ok body")) 
;; true

(ok? (internal-server-error "bad things")) 
;; false

(not-ok? (internal-server-error "bad things")) 
;; true

(not-found? (ok "found")) 
;; false
```

Retrieving status from documents:

```clojure
(<-status (ok {:id 123 :name "Stella"})) 
;; 200

(<-status (not-found {:id 123 :message "nope"})) 
;; 404
```

Retrieving body from documents:

```clojure
(<-body (ok "ok body")) 
;; "ok body"

(<-body (not-found {:id 123 :message "Unable to find item 123."})) 
;; {:id 123, :message "Unable to find item 123."}
```

Transforming request body to Clojure map (via headers/content-type):

```clojure
(def edn-request (request (content :edn) "{:foo :bar}"))
(body->map edn-request) 
;; {:foo :bar}

(def json-request (request (->> (content :json) (accept :transit)) "{\"foo\" : \"bar\"}"))
json-request
;; {:body "{\"foo\" : \"bar\"}", :headers {"Content-Type" "application/json", "Accept" "application/transit+json"}}
(body->map json-request) 
;; {:foo "bar"}

(def transit-request {:headers {"Content-Type" "application/transit+json"} :body "[\"^ \",\"~:foo\",\"~:bar\"]"})
;; could have used (content :transit) instead of explicit headers map value {}
(body->map transit-request) 
;; {:foo :bar}

(def xml-request {:headers (content :xml) :body "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>"})
(body->map xml-request) 
;; {:tag :foo, :attrs nil, :content ["bar"]}

(def yaml-request (request (content :yaml) "foo: bar"))
(body->map yaml-request) 
;; #ordered/map([:foo "bar"])
```

Transforming response body to accepted formatted string:

```clojure

;; (body->text $request-document $response-document)

(body->text {:headers {"Accept" "application/edn"}} (not-found {:id 123 :message "Item 123 not found."})) 
;; {:status 404, :body "{:id 123, :message \"Item 123 not found.\"}\n", :headers {"Content-Type" "application/edn"}}

(body->text (headers (accept :json)) (not-found {:id 123 :message "Item 123 not found."}))
;; {:status 404, :body "{\"id\":123,\"message\":\"Item 123 not found.\"}", :headers {"Content-Type" "application/json"}}

(body->text (request (accept :transit) nil) (not-found {:id 123 :message "Item 123 not found."}))
;; {:status 404,
;;  :body "[\"^ \",\"~:id\",123,\"~:message\",\"Item 123 not found.\"]",
;;  :headers {"Content-Type" "application/transit+json"}}

; note: xml handling is not pretty or elegant but supported by tophat
(body->text (headers (accept :xml)) (not-found {:tag :foo, :attrs nil, :content ["bar"]}))
;; {:status 404,
;;  :body "<?xml version='1.0' encoding='UTF-8'?>\n<foo>\nbar\n</foo>\n",
;;  :headers {"Content-Type" "application/xml"}}

(body->text (headers (accept :yaml)) (not-found {:id 123 :message "Item 123 not found."}))
;; {:status 404, :body "{id: 123, message: Item 123 not found.}\n", :headers {"Content-Type" "text/yaml"}}
```
### Middleware

Ring handlers may also be developed to automatically convert responses to the accepted format
designated in the original request.

Wrappers:
```clojure
(def file-regex #".*File")
(def stream-regex #".*InputStream")
(def http-input-regex #".*HttpInputOverHTTP")

(defn file-or-stream?
  [x]
  (when (some? x)
    (when-let [clss (str (class x))]
      (let [r? (or (re-find http-input-regex clss)
                   (re-find file-regex clss)
                   (re-find stream-regex clss))]
        r?))))
        
(defn wrap-response-body-using-accept-header
  "Converts the response body to the format in the request Accept: header."
  [handler & [_]]
  (fn [request]
    (let [response (handler request)
          fos? (file-or-stream? (get response :body))]
      (if fos?
        response
        (body->text request response)))))

(defn wrap-request-body-using-content-type-header
  "Converts the incoming request body to Clojure map given the request Content-Type: header."
  [handler & [_]]
  (fn [req]
    (handler (if (= (get req :request-method) :post)
               (try
                 (if (file-or-stream? (get req :body))
                   req
                   (assoc req :body (body->map req)))
                 (catch Exception e
                   req))
               req))))
```

Add to middleware handler:
```clojure
;; NOTE : the handlers are in REVERSE ORDER, meaning the wrapper functions are done from the bottom up...

(defn wrap-middleware [handler]
  (-> handler
      wrap-response-body-using-accept-header ;; transforms response clj map body to req accept format
      wrap-request-body-using-content-type-header ;; converts request body to clj map
      wrap-defaults
      wrap-exceptions
      wrap-reload))

```

### Let Macros

`if-let-ok` macro:

```clojure
(if-let-ok [r (ok "ok body")]
           (<-body r)
           :should-not-get-here)
;; "ok body"

(if-let-ok [r (not-found {:id 123 :msg "Item 123 not found."})]
           :should-not-get-here
           (<-body r))
;; {:id 123, :msg "Item 123 not found."}

```
`when-let-ok` macro:

```clojure
(when-let-ok [r (ok "ok body")]
             (<-body r))
;; "ok body"

(when-let-ok [r (not-found {:id 123 :msg "Item 123 not found."})]
             :should-not-get-here)
;; nil
```

`when-let-not-ok` macro:

```clojure
(when-let-not-ok [r (ok "ok body")]
                 :should-not-get-here)
;; nil

(when-let-not-ok [r (not-found {:id 123 :msg "Item 123 not found."})]
                 (<-body r))
;; {:id 123, :msg "Item 123 not found."}
```

### Lifting

Not all functions return HTTP responses so Tophat provides *lifting* functions.

*What is lifting?*

Similiar to lifting in monads (don't worry, you don't have to know monads),
lifting in Tophat wraps the non-HTTP-response returning function into a function that
returns an HTTP response document (with `:status` and `:body`) and also handles
exceptions by wrapping the exception in an HTTP response document as well.

The simplest lift of a function (using the `lift` function) returns 
an OK (200) response document if the lifted function returns a non-nil result 
with the result as the body,

```clojure
(lift + 2 3)
;; {:status 200 :status-text "OK" :body 5}
``` 

a Not-Found (404) response document if the lifted function result is `nil`, 

```clojure
(lift (fn [] nil))
;; {:status 404, :status-text "Not Found", :body nil}
```

and an Internal-Server-Error (500) and a distilled exception map as the body
if an exception is thrown.

```clojure
(lift / 1 0)
;; =>
{:status 500,
 :status-text "Internal Server Error",
 :body {:cause "Divide by zero",
        :via [{:type "class java.lang.ArithmeticException",
               :message "Divide by zero",
               :at "clojure.lang.Numbers.divide(Numbers.java:158)"}],
        :trace [{:class "tophat.core$lift_custom",
                 :file "core.clj",
                 :line 1246,
                 :text "tophat.core$lift_custom.invokeStatic(core.clj:1246)"}
                {:class "tophat.core$lift_custom",
                 :file "core.clj",
                 :line 1213,
                 :text "tophat.core$lift_custom.doInvoke(core.clj:1213)"}
                {:class "tophat.core$eval7978",
                 :file "form-init6867913379230085047.clj",
                 :line 1,
                 :text "tophat.core$eval7978.invokeStatic(form-init6867913379230085047.clj:1)"}],
        :exception-args (nil #object[clojure.core$_SLASH_ 0x27b1ff4b "clojure.core$_SLASH_@27b1ff4b"] (1 0))}}
```

The default `lift` function takes the function to be lifted as the first argument
and the arguments for that lifted function as variadic arguments (as many as
needed on the end; indefinite arity).

`lift` function signature:

```clojure
(defn lift [f & args]) 
;; where f is the function to be lifted and the args are the variadic arguments
```

The `lift` function may be called directly with the lifted function 
and arguments:

```clojure
(lift / 8 2)
;; {:status 200, :status-text "OK", :body 4}
```
... or a `partial` may be used to simplify code.

```clojure
(def lifted-divide (partial lift /))

(lifted-divide 8 2)
;;  {:status 200, :status-text "OK", :body 4}
```

The lifted function is also wrapped to handle exceptions:

```clojure
(lifted-divide 8 0)
;; =>
{:status 500,
 :status-text "Internal Server Error",
 :body {:cause "Divide by zero",
        :via [{:type "class java.lang.ArithmeticException",
               :message "Divide by zero",
               :at "clojure.lang.Numbers.divide(Numbers.java:158)"}],
        :trace [{:class "tophat.core$lift_custom",
                 :file "core.clj",
                 :line 1246,
                 :text "tophat.core$lift_custom.invokeStatic(core.clj:1246)"}
                {:class "tophat.core$lift_custom",
                 :file "core.clj",
                 :line 1213,
                 :text "tophat.core$lift_custom.doInvoke(core.clj:1213)"}
                {:class "tophat.core$eval7956",
                 :file "form-init6867913379230085047.clj",
                 :line 1,
                 :text "tophat.core$eval7956.invokeStatic(form-init6867913379230085047.clj:1)"}],
        :exception-args (nil #object[clojure.core$_SLASH_ 0x27b1ff4b "clojure.core$_SLASH_@27b1ff4b"] (8 0))}}
```

In addition to the default `lift` function, Tophat also provides a more
customizable lifting function (`lift-custom`) that takes an options map
as the first argument, the function to be lifted as the second argument,
and all arguments to be used when calling the lifted function as variadic (as
many as needed on the end).

`lift-custom` function signature:

```clojure
(defn lift-custom [options f & args])
;; where options is an options maps for handling the lift, the function f 
;; to be lifted, and the lifted function variadic arguments args
```

The `options` map has the following key-value configs, all are optional with
common sense defaults:

```clojure
{:non-nil-response-status :$valid-http-status-code ; defaults to 200 (OK)
 :result-handler-f :$function-with-result-options-f-args-parameters ; if none, pass thru non-nil result as :body
 :nil-response-status :$valid-http-status-code ; defaults to 404 (Not Found)
 :nil-response-body :$scalar-value-for-nil-responses ; if this is non-nil, uses this and ignores :nil-result-handler-f
 :nil-result-handler-f :$function-with-options-f-args-parameters ; if none, sets :body in response to nil
 :exception-status :$valid-http-status-code ; defaults to 500 (Internal Server Error)
 :exception-body-f :$function-with-exception-options-f-args} ; defaults to distilled exception map ->exception-info
```

So if you wanted to change the `status` codes for handling non-nil, nil, and exceptions, then you
could do a `partial` on `lift-custom` and other partials as needed:

```clojure
(def lift-custom-statuses (partial lift-custom {:non-nil-response-status created-status
                                                :nil-response-status bad-request-status
                                                :exception-status bad-gateway-status}))

(lift-custom-statuses + 2 3)
;; {:status 201, :status-text "Created", :body 5}

(def cs-nil (partial lift-custom-statuses (fn [] nil)))

(cs-nil)
;; {:status 400, :status-text "Bad Request", :body nil}

(def cs-div (partial lift-custom-statuses /))

(cs-div 8 2)
;; {:status 201, :status-text "Created", :body 4}

(cs-div 1 0)
;; =>
{:status 502,
 :status-text "Bad Gateway",
 :body {:cause "Divide by zero",
        :via [{:type "class java.lang.ArithmeticException",
               :message "Divide by zero",
               :at "clojure.lang.Numbers.divide(Numbers.java:158)"}],
        :trace [{:class "tophat.core$lift_custom",
                 :file "core.clj",
                 :line 1246,
                 :text "tophat.core$lift_custom.invokeStatic(core.clj:1246)"}
                {:class "tophat.core$lift_custom",
                 :file "core.clj",
                 :line 1213,
                 :text "tophat.core$lift_custom.doInvoke(core.clj:1213)"}
                {:class "tophat.core$eval7994",
                 :file "form-init6867913379230085047.clj",
                 :line 1,
                 :text "tophat.core$eval7994.invokeStatic(form-init6867913379230085047.clj:1)"}],
        :exception-args ({:non-nil-response-status 201, :nil-response-status 400, :exception-status 502}
                         #object[clojure.core$_SLASH_ 0x27b1ff4b "clojure.core$_SLASH_@27b1ff4b"]
                         (1 0))}}

``` 

Here are some tests for lifting in Tophat to get a better idea on how lifting
works in the library:

```clojure
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
```

And here are some examples of using different handler function 
options for `lift-custom`:

```clojure
  :result-handler-f

  (fn [r & _] (str "Converts result to string and ignores options, f, and args: " r))
  (fn [r options f args] {:result r :options options :f f :args args})

  :nil-result-handler-f

  (fn [& _] "Result is nil.") ; same as using the :nil-response-body "Result is nil." in options
  (fn [options f args] {:options options :f f :args args :message "Result was nil."})

  :exception-body-f

  (fn [e & _] (.getMessage e)) ; just sets response :body to exception message
  (fn [e options f args] (str "Exception occurred with options: " options ", f " f ", args " args " [" e "]))
```

## License

Copyright © 2017 SierraLogic LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
