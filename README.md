# tophat

Tophat is a Clojure library for handling HTTP request and response documents/maps.

## Rationale

Tophat began as a utility library that was passed between projects to handle
HTTP request and response documents.

As the library evolved, the request/response maps (aka Ring maps) were not only being used
to handle HTTP-based handling, but also by function for results that can be processed
based on statuses.

## Usage

In `project.clj` dependencies:

```clojure
[tophat "0.1.3"]

```

In code:

```clojure
(ns your-ns
  (:require [tophat.core :as tophat]))

```

## Examples

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
;; (response $body) or (response $header-map $body)
;; (ok $body) or (ok $header-map $body)

(ok {"Pragma" "no-cache"} "this is ok body") 
;; {:status 200, :body "this is ok body", :headers {"Pragma" "no-cache"}} 

(ok (pragma "no-cache") "this is ok body") 
;; {:status 200, :body "this is ok body", :headers {"Pragma" "no-cache"}} 

(ok (->> (content :text) (pragma "no-cache")) "this is ok body") 
;; {:status 200, :body "this is ok body", :headers {"Content-Type" "text/plain" "Pragma" "no-cache"}} 

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

## License

Copyright © 2017 SierraLogic LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
