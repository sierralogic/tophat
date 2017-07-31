# tophat

Tophat is a Clojure library for handling HTTP request and response documents/maps.

## Rationale

Tophat began as a utility library that was passed between projects to handle
HTTP request and response documents.

As the library evolved, the HTTP response maps (aka Ring maps) were being used
more and more as results from function calls to handle exception handling
so it made sense to break out Tophat into a top-level library/project. 

## Usage

In `project.clj` dependencies:

```clojure
[tophat "0.1.2"]

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
(ok {"Pragma" "no-cache"} "this is ok body") ;; {:status 200, :body "this is ok body", :headers {"Pragma" "no-cache"}} 
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
(def edn-request {:headers {"content-type" "application/edn"} :body "{:foo :bar}"}) ;; could have used edn-content as headers value
(body->map edn-request) 
;; {:foo :bar}

(def json-request {:headers json-content} :body "{\"foo\" : \"bar\"}"})
(body->map json-request) 
;; {:foo "bar"}

(def transit-request {:headers transit-content :body "[\"^ \",\"~:foo\",\"~:bar\"]"})
(body->map transit-request) 
;; {:foo :bar}

(def xml-request {:headers xml-content :body "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>"})
(body->map xml-request) 
;; {:tag :foo, :attrs nil, :content ["bar"]}

(def yaml-request {:headers yaml-content :body "foo: bar"})
(body->map yaml-request) 
;; #ordered/map([:foo "bar"])
```

Transforming response body to accepted formatted string:

```clojure
xml-accept ;; {:headers {"accept" "application/xml"}}

(body->text edn-accept (not-found {:id 123 :message "Item 123 not found."})) 
;; {:status 404, :body "{:id 123, :message \"Item 123 not found.\"}\n", :headers {"Content-Type" "application/edn"}}

(body->text json-accept (not-found {:id 123 :message "Item 123 not found."})) 
;; {:status 404, :body "{\"id\":123,\"message\":\"Item 123 not found.\"}", :headers {"Content-Type" "application/json"}}

(body->text transit-accept (not-found {:id 123 :message "Item 123 not found."}))
;; {:status 404,
;;  :body "[\"^ \",\"~:id\",123,\"~:message\",\"Item 123 not found.\"]",
;;  :headers {"Content-Type" "application/transit+json"}}

; note: xml handling is not pretty or elegant but supported by tophat
(body->text xml-accept (not-found {:tag :foo, :attrs nil, :content ["bar"]}))
;; {:status 404,
;;  :body "<?xml version='1.0' encoding='UTF-8'?>\n<foo>\nbar\n</foo>\n",
;;  :headers {"Content-Type" "application/xml"}}

(body->text yaml-accept (not-found {:id 123 :message "Item 123 not found."}))
;; {:status 404, :body "{id: 123, message: Item 123 not found.}\n", :headers {"Content-Type" "text/yaml"}}
```

### Handling Macros

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
