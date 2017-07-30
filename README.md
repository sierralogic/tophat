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

### Creating response documents

;; todo

### Handling documents

;; todo

### Processing response documents from internal functions
 

## License

Copyright © 2017 SierraLogic LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
