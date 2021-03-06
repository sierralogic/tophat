(ns tophat.core
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [xander.core :as xander]
            [yaml.core :as yaml])
  (:import (java.io ByteArrayOutputStream StringWriter)))

(def default-call-protocol :http)
(def protocol-regex #"(.*)://([^?]*)([?]*)(.*)")
(def regex-equals #"=")
(def regex-ampersand #"&")

;; utilities

(defn lvs? [x] (or (vector? x) (set? x) (list? x) (seq? x)))
(defn third [l] (-> l rest second))
(defn fourth [l] (-> l rest rest second))
(defn fifth [l] (-> l rest rest rest second))

(defn ->kw [k] (when k (if (keyword? k) k (keyword (-> (str k) str/lower-case (str/replace #"\s" "-"))))))
(defn pprint-string
  "Returns a pretty printed (pprint) string of x."
  [x]
  (let [w (StringWriter.)]
    (pprint x w)
    (.toString w)))

(defn ->input-stream
  "Coverts x to a string and then into an input stream."
  [x]
  (io/input-stream (.getBytes (str x))))

(defn ->cxml
  "Converts source XML file x or raw string XML x to interim Clojure XML representation."
  [x]
  (let [s (if (str/starts-with? x "<?")
            (->input-stream x)
            x)]
    (xander/xml->map s nil true)))

(defn ->yaml
  [m]
  (yaml/generate-string m))

(defn trace-dup?
  [t1 t2]
  (and (= (:class t1) (:class t2))
       (= (:file t1) (:file t2))
       (= (:line t1) (:line t2))))

(defn collapse-stack-trace
  [ts]
  (loop [prev nil
         acc []
         traces ts]
    (if (empty? traces)
      acc
      (recur (first traces)
             (if (trace-dup? prev (first traces))
               acc
               (conj acc (first traces)))
             (rest traces)))))

(defn trace->map
  [t]
  {:class (.getClassName t)
   :file (.getFileName t)
   :line (.getLineNumber t)
   ;; :method (.getMethodName t)
   ;; :native? (.isNativeMethod t)
   :text (str t)})

(defn truesy [_] true)

(defn no-clojure-or-java-traces
  [x]
  (not (or (str/starts-with? (:class x) "clojure.")
           (str/starts-with? (:class x) "sun.")
           (str/starts-with? (:class x) "java."))))

(defn ->emap
  [em & [filtr]]
  (-> em
      (update :via #(reduce (fn [a v] (conj (or a []) (-> v
                                                          (update :type str)
                                                          (update :at str))))
                            nil
                            %))
      (update :trace #(vec (collapse-stack-trace (filter (or filtr truesy)
                                                         (map trace->map %)))))))

(defn ->exception-info
  [e & args]
  (assoc (->emap (Throwable->map e) no-clojure-or-java-traces) :exception-args args))

;; status

;; 1xx Informational
(def continue-status 100)
(def switching-protocols-status 101)
(def processing-status 102)

;; 2×× Success
(def ok-status 200)
(def created-status 201)
(def accepted-status 202)
(def no-content-status 203)
(def non-authoritative-information-status 204)
(def reset-content-status 205)
(def partial-content-status 206)
(def multi-status-status 207)
(def already-reported-status 208)
(def im-used-status 226)

;; 3×× Redirection
(def multiple-choices-status 300)
(def moved-permanently-status 301)
(def found-status 302)
(def see-other-status 303)
(def not-modified-status 304)
(def use-proxy-status 305)
(def temporary-redirect-status 307)
(def permanent-redirect-status 308)

;; 4×× Client Error
(def bad-request-status 400)
(def unauthorized-status 401)
(def payment-required-status 402)
(def forbidden-status 403)
(def not-found-status 404)
(def method-not-allowed-status 405)
(def not-acceptable-status 406)
(def proxy-authentication-required-status 407)
(def request-timeout-status 408)
(def conflict-status 409)
(def gone-status 410)
(def length-required-status 411)
(def precondition-failed-status 412)
(def payload-too-large-status 413)
(def request-uri-too-long-status 414)
(def unsupported-media-type-status 415)
(def requested-range-not-satisfiable-status 416)
(def expectation-failed-status 417)
(def im-a-teapot-status 418)
(def misdirected-request-status 421)
(def unprocessable-entity-status 422)
(def locked-status 423)
(def failed-dependency-status 424)
(def upgrade-required-status 426)
(def precondition-required-status 428)
(def too-many-requests-status 429)
(def request-header-fields-too-large-status 431)
(def connection-closed-without-response-status 444)
(def unavailable-for-legal-reasons-status 451)
(def client-closed-request-status 499)

;; 5×× Server Error
(def internal-server-error-status 500)
(def not-implemented-status 501)
(def bad-gateway-status 502)
(def service-unavailable-status 503)
(def gateway-timeout-status 504)
(def http-version-not-supported-status 505)
(def variant-also-negotiates-status 506)
(def insufficient-storage-status 507)
(def loop-detected-status 508)
(def not-extended-status 510)
(def network-authentication-required-status 511)
(def network-connect-timeout-error-status 599)

(def ok-key :ok)
(def created-key :created)
(def accepted-key :accepted)
(def bad-request-key :bad-request)
(def not-found-key :not-found)
(def internal-server-error-key :internal-server-error)
(def not-implemented-key :not-implemented)
(def bad-gateway-key :bad-gateway)

(def informationals [continue-status switching-protocols-status processing-status])

(def successes [ok-status created-status accepted-status no-content-status non-authoritative-information-status
                reset-content-status partial-content-status multi-status-status already-reported-status im-used-status])

(def redirections [multiple-choices-status moved-permanently-status found-status see-other-status not-modified-status
                   use-proxy-status temporary-redirect-status permanent-redirect-status])

(def client-errors [bad-request-status unauthorized-status payment-required-status forbidden-status not-found-status
                    method-not-allowed-status not-acceptable-status proxy-authentication-required-status
                    request-timeout-status conflict-status gone-status length-required-status precondition-failed-status
                    payload-too-large-status request-uri-too-long-status unsupported-media-type-status
                    requested-range-not-satisfiable-status expectation-failed-status im-a-teapot-status
                    misdirected-request-status unprocessable-entity-status locked-status failed-dependency-status
                    upgrade-required-status precondition-required-status too-many-requests-status
                    request-header-fields-too-large-status connection-closed-without-response-status
                    unavailable-for-legal-reasons-status client-closed-request-status])

(def server-errors [internal-server-error-status not-implemented-status bad-gateway-status
                    service-unavailable-status gateway-timeout-status http-version-not-supported-status
                    variant-also-negotiates-status insufficient-storage-status loop-detected-status
                    not-extended-status network-authentication-required-status
                    network-connect-timeout-error-status])

(def valid-statuses (vec (concat informationals successes redirections
                                 client-errors server-errors)))

(def valid-status-m (reduce (fn [a x] (assoc a x x)) nil valid-statuses))

(defn valid?
  "Determine if status s is valid status code."
  [s]
  (contains? valid-status-m s))

(def reverse-status {ok-key ok-status created-key created-status accepted-key accepted-status
                     bad-request-key bad-request-status not-found-key not-found-status
                     internal-server-error-key internal-server-error-status not-implemented-key not-implemented-status
                     bad-gateway-key bad-gateway-status})

(def status-texts {continue-status "Continue" switching-protocols-status "Switching Protocols" processing-status "Processing"
                   ok-status "OK" created-status "Created" accepted-status "Accepted"
                   non-authoritative-information-status "Non-Authoritative Information" no-content-status "No Content"
                   reset-content-status "Reset Content" partial-content-status "Partial Content"
                   multi-status-status "Multi-Status" already-reported-status "Already Reported" im-used-status "IM Used"
                   multiple-choices-status "Multiple Choices" moved-permanently-status "Moved Permanently" found-status "Found"
                   see-other-status "See Other" not-modified-status "Not Modified" use-proxy-status "Use Proxy"
                   temporary-redirect-status "Temporary Redirect" permanent-redirect-status "Permanent Redirect"
                   bad-request-status "Bad Request" unauthorized-status "Unauthorized" payment-required-status "Payment Required"
                   forbidden-status "Forbidden" not-found-status "Not Found" method-not-allowed-status "Method Not Allowed"
                   not-acceptable-status "Not Acceptable" proxy-authentication-required-status "Proxy Authentication Required"
                   request-timeout-status "Request Timeout" conflict-status "Conflict" gone-status "Gone" length-required-status "Length Required"
                   precondition-failed-status "Precondition Failed" payload-too-large-status "Payload Too Large"
                   request-uri-too-long-status "URI Too Long" unsupported-media-type-status "Unsupported Media Type"
                   requested-range-not-satisfiable-status "Range Not Satisfiable" expectation-failed-status "Expectation Failed"
                   im-a-teapot-status "I'm a teapot" misdirected-request-status "Misdirected Request" unprocessable-entity-status "Unprocessable Entity"
                   locked-status "Locked" failed-dependency-status "Failed Dependency" upgrade-required-status "Upgrade Required"
                   precondition-required-status "Precondition Required" too-many-requests-status "Too Many Requests"
                   request-header-fields-too-large-status "Request Header Fields Too Large" unavailable-for-legal-reasons-status "Unavailable For Legal Reasons"
                   internal-server-error-status "Internal Server Error" not-implemented-status "Not Implemented" bad-gateway-status "Bad Gateway"
                   service-unavailable-status "Service Unavailable" gateway-timeout-status "Gateway Timeout"
                   http-version-not-supported-status "HTTP Version Not Supported" variant-also-negotiates-status "Variant Also Negotiates"
                   insufficient-storage-status "Insufficient Storage" loop-detected-status "Loop Detected"
                   not-extended-status "Not Extended" network-authentication-required-status "Network Authentication Required"})

(defn get-status-code
  "Returns status numeric code given keyword status key c."
  [c]
  (get reverse-status c internal-server-error-status))

(defn get-status-code-text
  "Returns statue text given a keyword status key c."
  [c]
  (get status-texts (get-status-code c)))

(defn status-text
  "Returns the status text for given HTTP status x."
  [x]
  (get status-texts x (str "Unknown HTTP status code " x ".")))

(defn parse-query-string
  "Parse an HTTP query string."
  [s]
  (if s
    (reduce (fn [a x]
              (if (not (empty? x))
                (let [sp (str/split x regex-equals)
                      k (first sp)
                      v (or (second sp) true)
                      p (get a k)]
                  (assoc a k (if p (if (lvs? p) (conj p v) [p v]) v)))
                a))
            nil
            (str/split s regex-ampersand))))

(defn get-scheme
  "Extracts the protocol from a link string."
  [l]
  (or (->kw (second (re-find protocol-regex l))) default-call-protocol))

(defn get-params
  "Extracts parameters from query parameter string.
  ex. (get-parameters \"foo=bar&baz=23&ans=42\") => {:foo \"bar\" :baz \"23\" :ans \"42\"}"
  [qps]
  (reduce #(assoc % (->kw (first %2)) (if (lvs? (second %2)) (last (second %2)) (second %2)))
          {}
          qps))

(defn s2i
  "Converts string s to integer.  Returns nil if unable to convert s to integer."
  [s]
  (try
    (Integer. (re-find #"\d+" s))
    (catch Exception e)))

(defn parse-to-request
  "Parse an HTTP-style endpoint to extract protocol, call, query string, and query parameters."
  [l & {:keys [local?]}]
  (let [p (re-find protocol-regex l)
        protocol (or (->kw (second p)) default-call-protocol)
        call (third p)
        call-split (str/split call #"/")
        ref (re-find #"([^:]*)(:*)(.*)" (or (if (not local?) (first call-split)) ""))
        server-name (if (not (empty? (second ref))) (second ref))
        port (s2i (if (not (empty? (fourth ref))) (fourth ref)))
        uri (str "/" (str/join "/" (if local? call-split (rest call-split))))
        query-s (or (fifth p) nil)
        query-ps (parse-query-string query-s)
        params (get-params query-ps)]
    {:scheme protocol
     :call call
     :server-name server-name
     :uri uri
     :server-port port
     :query-string query-s
     :query-params query-ps
     :params params}))

;; mime types
(def mimetype-edn "application/edn")
(def mimetype-html "text/html")
(def mimetype-json "application/json")
(def mimetype-text "text/plain")
(def mimetype-transit-json "application/transit+json")
(def mimetype-transit-jsonx "application/transit\\+json")
(def mimetype-xml "application/xml")
(def mimetype-yaml "text/yaml")

(def supported-mimetypes {:edn mimetype-edn
                          :html mimetype-html
                          :htm mimetype-html
                          :json mimetype-json
                          :text mimetype-text
                          :string mimetype-text
                          :transit mimetype-transit-json
                          :xml mimetype-xml
                          :yaml mimetype-yaml})

(def pre-ct "(^|;)")
(def post-ct "($|;)")

(def mt-edn-regex (re-pattern (str pre-ct mimetype-edn post-ct)))
(def mt-html-regex (re-pattern (str pre-ct mimetype-html post-ct)))
(def mt-json-regex (re-pattern (str pre-ct mimetype-json post-ct)))
(def mt-text-regex (re-pattern (str pre-ct mimetype-text post-ct)))
(def mt-transit-regex (re-pattern (str pre-ct mimetype-transit-jsonx post-ct)))
(def mt-xml-regex (re-pattern (str pre-ct mimetype-xml post-ct)))
(def mt-yaml-regex (re-pattern (str pre-ct mimetype-yaml post-ct)))

;; header / content types

;; header fields

(def header-accept "Accept")
(def header-accept-charset "Accept-Charset")
(def header-accept-encoding "Accept-Encoding")
(def header-accept-language "Accept-Language")
(def header-accept-datetime "Accept-Datetime")
(def header-access-control-request-method "Accept-Control-Request-Method")
(def header-access-control-request-headers "Accept-Control-Request-Headers")
(def header-authorization "Authorization")
(def header-cache-control "Cache-Control")
(def header-connection "Connection")
(def header-cookie "Cookie")
(def header-content-length "Content-Length")
(def header-content-md5 "Content-MD5")
(def header-content-type "Content-Type")
(def header-date "Date")
(def header-expect "Expect")
(def header-forwarded "Forwarded")
(def header-from "From")
(def header-host "Host")
(def header-if-match "If-Match")
(def header-if-modified-since "If-Modified-Since")
(def header-if-none-match "If-None-Match")
(def header-if-range "If-Range")
(def header-if-unmodified-since "If-Unmodified-Since")
(def header-max-forwards "Max-Forwards")
(def header-origin "Origin")
(def header-pragma "Pragma")
(def header-proxy-authorization "Proxy-Authorization")
(def header-range "Range")
(def header-referer "Referer")
(def header-te "TE")
(def header-user-agent "User-Agent")
(def header-upgrade "Upgrade")
(def header-via "Via")
(def header-warning "Warning")

;; response header fields
(def header-access-control-access-origin "Access-Control-Allow-Origin")
(def header-access-control-allow-credentials "Access-Control-Allow-Credentials")
(def header-access-control-expose-headers "Access-Control-Expose-Headers")
(def header-access-control-max-age "Access-Control-Max-Age")
(def header-access-control-allow-methods "Access-Control-Allow-Methods")
(def header-access-control-allow-headers "Access-Control-Allow-Headers")
(def header-accept-patch "Accept-Patch")
(def header-accept-ranges "Accept-Ranges")
(def header-age "Age")
(def header-allow "Allow")
(def header-alt-svc "Alt-Svc")
(def header-cache-control "Cache-Control")
(def header-connection "Connection")
(def header-content-disposition "Content-Disposition")
(def header-content-encoding "Content-Encoding")
(def header-content-language "Content-Language")
(def header-content-length "Content-Length")
(def header-content-location "Content-Location")
(def header-content-md5 "Content-MD5")
(def header-content-range "Content-Range")
(def header-date "Date")
(def header-etag "ETag")
(def header-expires "Expires")
(def header-last-modified "Last-Modified")
(def header-link "Link")
(def header-location "Location")
(def header-p3p "P3P")
(def header-proxy-authenticate "Proxy-Authenticate")
(def header-public-key-pins "Public-Key-Pins")
(def header-retry-after "Retry-After")
(def header-server "Server")
(def header-set-cookie "Set-Cookie")
(def header-strict-transport-security "Strict-Transport-Security")
(def header-trailer "Trailer")
(def header-transfer-encoding "Transfer-Encoding")
(def header-tk "Tk")
(def header-upgrade "Upgrade")
(def header-vary "Vary")
(def header-via "Via")
(def header-warning "Warning")
(def header-www-authenticate "WWW-Authenticate")

;; non-standard http header fields
(def header-dnt "DNT")
(def header-refresh "Refresh")
(def header-status "Status")
(def header-powered-by "X-Powered-By")
(def header-request-id "X-Request-ID")
(def header-xss-protection "X-XSS-Protection")

;; ring request header fields
(def ring-req-accept "accept")
(def ring-req-accept-charset "accept-charset")
(def ring-req-accept-encoding "accept-encoding")
(def ring-req-accept-language "accept-language")
(def ring-req-accept-datetime "accept-datetime")
(def ring-req-access-control-request-method "accept-control-request-method")
(def ring-req-authorization "authorization")
(def ring-req-cache-control "cache-control")
(def ring-req-connection "connection")
(def ring-req-cookie "cookie")
(def ring-req-content-length "content-length")
(def ring-req-content-md5 "content-md5")
(def ring-req-content-type "content-type")
(def ring-req-date "date")
(def ring-req-expect "expect")
(def ring-req-forwarded "forwarded")
(def ring-req-from "from")
(def ring-req-host "host")
(def ring-req-if-match "if-match")
(def ring-req-if-modified-since "if-modified-since")
(def ring-req-if-none-match "if-none-match")
(def ring-req-if-range "if-range")
(def ring-req-if-unmodified-since "if-unmodified-since")
(def ring-req-max-forwards "max-forwards")
(def ring-req-origin "origin")
(def ring-req-pragma "pragma")
(def ring-req-proxy-authorization "proxy-authorization")
(def ring-req-range "range")
(def ring-req-referer "referer")
(def ring-req-te "te")
(def ring-req-user-agent "user-agent")
(def ring-req-upgrade "upgrade")
(def ring-req-via "via")
(def ring-req-warning "warning")
(def ring-req-dnt "dnt")

;; ------------------------------------------------
(def edn-content {header-content-type mimetype-edn})
(def html-content {header-content-type mimetype-html})
(def json-content {header-content-type mimetype-json})
(def text-content {header-content-type mimetype-text})
(def transit-content {header-content-type mimetype-transit-json})
(def xml-content {header-content-type mimetype-xml})
(def yaml-content {header-content-type mimetype-yaml})

(def edn-accept {:headers {header-accept mimetype-edn}})
(def html-accept {:headers {header-accept mimetype-html}})
(def json-accept {:headers {header-accept mimetype-json}})
(def text-accept {:headers {header-accept mimetype-text}})
(def transit-accept {:headers {header-accept mimetype-transit-json}})
(def xml-accept {:headers {header-accept mimetype-xml}})
(def yaml-accept {:headers {header-accept mimetype-yaml}})

;; header funcs
(defn headers
  [m]
  {:headers m})

(defn <-header
  "Extracts header value from request or response and returns the first non-nil values from
  the headers for header fields hs."
  [rr & hs]
  (when-let [rhs (get rr :headers)]
    (reduce (fn [_ h]
              (when-let [v (get rhs h)]
                (reduced v)))
            nil
            hs)))

(defn <-header-field
  [rr h]
  (let [lh (str/lower-case h)]
  (<-header rr h lh (keyword lh))))

;; extract header fields from req/res
(defn <-accept [r] (or (<-header-field r header-accept) ""))
(defn <-accept-charset [r] (<-header-field r header-accept-charset))
(defn <-accept-encoding [r] (<-header-field r header-accept-encoding))
(defn <-accept-language [r] (<-header-field r header-accept-language))
(defn <-accept-datetime [r] (<-header-field r header-accept-datetime))
(defn <-accept-control-request-method [r] (<-header-field r header-access-control-request-method))
(defn <-authorization [r] (<-header-field r header-authorization))
(defn <-cache-control [r] (<-header-field r header-cache-control))
(defn <-connection [r] (<-header-field r header-connection))
(defn <-content-length [r] (<-header-field r header-content-length))
(defn <-content-md5 [r] (<-header-field r header-content-md5))
(defn <-content-type [r] (<-header-field r header-content-type))
(defn <-cookie [r] (<-header-field r header-cookie))
(defn <-date [r] (<-header-field r header-date))
(defn <-expect [r] (<-header-field r header-expect))
(defn <-forwarded [r] (<-header-field r header-forwarded))
(defn <-from [r] (<-header-field r header-from))
(defn <-host [r] (<-header-field r header-host))
(defn <-if-match [r] (<-header-field r header-if-match))
(defn <-if-modified-since [r] (<-header-field r header-if-modified-since))
(defn <-if-none-match [r] (<-header-field r header-if-none-match))
(defn <-if-range [r] (<-header-field r header-if-range))
(defn <-if-unmodified-since [r] (<-header-field r header-if-unmodified-since))
(defn <-max-forwards [r] (<-header-field r header-max-forwards))
(defn <-origin [r] (<-header-field r header-origin))
(defn <-pragma [r] (<-header-field r header-pragma))
(defn <-proxy-authorization [r] (<-header-field r header-proxy-authorization))
(defn <-range [r] (<-header-field r header-range))
(defn <-referer [r] (<-header-field r header-referer))
(defn <-te [r] (<-header-field r header-te))
(defn <-user-agent [r] (<-header-field r header-user-agent))
(defn <-dnt [r] (<-header-field r header-dnt))

(defn <-access-control-access-origin [r] (<-header-field r header-access-control-access-origin))
(defn <-access-control-allow-credentials [r] (<-header-field r header-access-control-allow-credentials))
(defn <-access-control-expose-headers [r] (<-header-field r header-access-control-expose-headers))
(defn <-access-control-max-age [r] (<-header-field r header-access-control-max-age))
(defn <-access-control-allow-methods [r] (<-header-field r header-access-control-allow-methods))
(defn <-acccess-control-allow-headers [r] (<-header-field r header-access-control-allow-headers))
(defn <-accept-patch [r] (<-header-field r header-accept-patch))
(defn <-accept-ranges [r] (<-header-field r header-accept-ranges))
(defn <-age [r] (<-header-field r header-age))
(defn <-allow [r] (<-header-field r header-allow))
(defn <-alt-svc [r] (<-header-field r header-alt-svc))
(defn <-content-disposition [r] (<-header-field r header-content-disposition))
(defn <-content-encoding [r] (<-header-field r header-content-encoding))
(defn <-content-language [r] (<-header-field r header-content-language))
(defn <-content-length [r] (<-header-field r header-content-length))
(defn <-content-location [r] (<-header-field r header-content-location))
(defn <-content-range [r] (<-header-field r header-content-range))
(defn <-etag [r] (<-header-field r header-etag))
(defn <-expires [r] (<-header-field r header-expires))
(defn <-last-modified [r] (<-header-field r header-last-modified))
(defn <-link [r] (<-header-field r header-link))
(defn <-location [r] (<-header-field r header-location))
(defn <-p3p [r] (<-header-field r header-p3p))
(defn <-proxy-authenticate [r] (<-header-field r header-proxy-authenticate))
(defn <-public-key-pins [r] (<-header-field r header-public-key-pins))
(defn <-retry-after [r] (<-header-field r header-retry-after))
(defn <-server [r] (<-header-field r header-server))
(defn <-set-cookie [r] (<-header-field r header-set-cookie))
(defn <-strict-transport-security [r] (<-header-field r header-strict-transport-security))
(defn <-trailer [r] (<-header-field r header-trailer))
(defn <-transfer-encoding [r] (<-header-field r header-transfer-encoding))
(defn <-tk [r] (<-header-field r header-tk))
(defn <-upgrade [r] (<-header-field r header-upgrade))
(defn <-vary [r] (<-header-field r header-vary))
(defn <-via [r] (<-header-field r header-via))
(defn <-warning [r] (<-header-field r header-warning))
(defn <-www-authenticate [r] (<-header-field r header-www-authenticate))
(defn <-refresh [r] (<-header-field r header-refresh))
(defn <-header-status [r] (<-header-field r header-status))
(defn <-powered-by [r] (<-header-field r header-powered-by))
(defn <-request-id [r] (<-header-field r header-request-id))
(defn <-xss-protection [r] (<-header-field r header-xss-protection))

(defn accepts?
  "Determines if HTTP request req Accept header matches mimetype regex rgx."
  [req rgx]
  (when req (re-find rgx (<-accept req))))
(defn accepts-edn? "Determine if HTTP request req accepts EDN." [req] (accepts? req mt-edn-regex))
(defn accepts-html? "Determine if HTTP request req accepts HTML." [req] (accepts? req mt-html-regex))
(defn accepts-json? "Determine if HTTP request req accepts JSON." [req] (accepts? req mt-json-regex))
(defn accepts-text? "Determine if HTTP request req accepts text." [req] (accepts? req mt-text-regex))
(defn accepts-transit? "Determine if HTTP request r accepts Transit." [req] (accepts? req mt-transit-regex))
(defn accepts-xml? "Determine if HTTP request req accepts XML." [req] (accepts? req mt-xml-regex))
(defn accepts-yaml? "Determine if HTTP request req accepts YAML." [req] (accepts? req mt-yaml-regex))

(defn content?
  "Determines if content type of HTTP request/response document rr matches content regex rgx."
  [rr rgx]
  (when (and rr rgx)
    (when-let [ct (<-content-type rr)]
      (re-find rgx ct))))

(defn edn-content? "Determines if content type of HTTP request req is EDN." [req] (content? req mt-edn-regex))
(defn json-content? "Determines if content type of HTTP request req is JSON." [req] (content? req mt-json-regex))
(defn transit-content? "Determines if content type of HTTP request req is Transit." [req] (content? req mt-transit-regex))
(defn xml-content? "Determines if content type of HTTP request req is XML." [req] (content? req mt-xml-regex))
(defn yaml-content? "Determines if content type of HTTP request req is YAML." [req] (content? req mt-yaml-regex))

(defn ->json-str
  "Generate a JSON string from map m."
  [m]
  (json/generate-string m))

(defn ->json-input-stream
  "Convert map m to JSON string stream."
  [m]
  (->input-stream (->json-str m)))

;; request

(defn ->input-stream
  "Converts string s to input stream."
  [s]
  (io/input-stream (.getBytes s)))

(defn body->map
  "Converts the body of the HTTP request document r to Clojure map."
  [req]
  (when-let [b (get req :body)]
    (cond
      (map? b) (walk/keywordize-keys b)
      (edn-content? req) (walk/keywordize-keys (read-string (if (string? b) b (slurp b))))
      (json-content? req) (json/parse-string (if (string? b) b (slurp b)) true)
      (xml-content? req) (->cxml b)
      (transit-content? req) (-> (if (string? b) (->input-stream b) b)
                                 (transit/reader :json)
                                 transit/read)
      (yaml-content? req) (yaml/parse-string (if (string? b) b (slurp b)))
      :else (json/parse-string (if (string? b) b (slurp b)) true))))

;; body conversions

(defn ->xml-str
  "Converts a Clojure map m to XML string."
  [m]
  (xander/map->xml m))

(defn ->transit
  "Converts a Clojure map m to transit string."
  [m]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer m)
    (.toString out)))

(defn body->text
  "Converts HTTP response document res body to appropriate text given the
  HTTP request req Accepts header content."
  [req res]
  (let [uf (cond
             (accepts-edn? req) [pprint-string edn-content]
             (accepts-html? req) [str html-content]
             (accepts-json? req) [json/generate-string json-content]
             (accepts-text? req) [str text-content]
             (accepts-transit? req) [->transit transit-content]
             (accepts-xml? req) [->xml-str xml-content]
             (accepts-yaml? req) [yaml/generate-string yaml-content]
             :else (if (= :get (get req :request-method))
                     [str html-content]
                     [str text-content]))]
    (-> res
        (update :body (first uf))
        (update :headers merge (second uf)))))

(defn body->transit
  "Converts HTTP response r body to transit format."
  [r]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)
        b (get r :body)]
    (transit/write writer b)
    (assoc r :body (.toString out))))

;; header generation

(defn header-field
  "Added header field k with value x and merges with optional headers map hm."
  [k x & [hm]]
  (merge hm {k x}))

(defn accept
  "Generates a request header for accept for single arguments,
  Merges existing map h with accept x.
  Mimetype x may be supported type keyword: :json, :edn, :html, :text, :xml, :yaml"
  [x & [hm]] (header-field header-accept (if (keyword? x) (get supported-mimetypes x x) x) hm))

(defn content-type
  "Generates a request header for content-type x for single arguments,
  Merges existing map h with content-type x.
  Mimetype x may be supported type keyword: :json, :edn, :html, :text, :xml, :yaml"
  [x & [hm]] (header-field header-content-type (if (keyword? x) (get supported-mimetypes x x) x) hm))
(defn content [x & [hm]] (content-type x hm))

(defn pragma [x & [hm]] (header-field header-pragma x hm))
(defn user-agent [x & [hm]] (header-field header-user-agent x hm))

;; requests ================================================================

(defn request
  "Generates HTTP request document."
  ([b] (request nil b))
  ([h b]
   (merge {:body b} (when (map? h) {:headers h}))))

;; responses ===============================================================

(defn response
  "Generate HTTP response document."
  ([b] (response ok-status nil b))
  ([s b] (response s nil b))
  ([s h b]
   (merge {:status s :status-text (status-text s) :body b} (when (map? h) {:headers h}))))

(defn accepted
  "Generate HTTP response document with status ACCEPTED."
  ([b] (accepted nil b))
  ([h b] (response accepted-status h b)))

(defn already-reported
  "Generate HTTP response document with status ALREADY REPORTED."
  ([b] (already-reported nil b))
  ([h b] (response already-reported-status h b)))

(defn bad-gateway
  "Generate HTTP response document with status BAD GATEWAY."
  ([b] (bad-gateway nil b))
  ([h b] (response bad-gateway-status h b)))

(defn bad-request
  "Generate HTTP response document with status BAD REQUEST."
  ([b] (bad-request nil b))
  ([h b] (response bad-request-status h b)))

(defn conflict
  "Generate HTTP response document with status CONFLICT."
  ([b] (conflict nil b))
  ([h b] (response conflict-status h b)))

(defn continue
  "Generate HTTP response document with status CONTINUE."
  ([b] (continue nil b))
  ([h b] (response continue-status h b)))

(defn created
  "Generate HTTP response document with status CREATED."
  ([b] (created nil b))
  ([h b] (response created-status h b)))

(defn expectation-failed
  "Generate HTTP response document with status EXPECTATION FAILED."
  ([b] (expectation-failed nil b))
  ([h b] (response expectation-failed-status h b)))

(defn failed-dependency
  "Generate HTTP response document with status FAILED DEPENDENCY."
  ([b] (failed-dependency nil b))
  ([h b] (response failed-dependency-status h b)))

(defn forbidden
  "Generate HTTP response document with status FORBIDDEN."
  ([b] (forbidden nil b))
  ([h b] (response forbidden-status h b)))

(defn found
  "Generate HTTP response document with status FOUND."
  ([b] (found nil b))
  ([h b] (response found-status h b)))

(defn gateway-timeout
  "Generate HTTP response document with status GATEWAY TIMEOUT."
  ([b] (gateway-timeout nil b))
  ([h b] (response gateway-timeout-status h b)))

(defn gone
  "Generate HTTP response document with status GONE."
  ([b] (gone nil b))
  ([h b] (response gone-status h b)))

(defn http-version-not-supported
  "Generate HTTP response document with status HTTP VERSION NOT SUPPORTED."
  ([b] (http-version-not-supported nil b))
  ([h b] (response http-version-not-supported-status h b)))

(defn im-a-teapot
  "Generate HTTP response document with status I'M A TEAPOT."
  ([b] (im-a-teapot nil b))
  ([h b] (response im-a-teapot-status h b)))

(defn im-used
  "Generate HTTP response document with status IM USED."
  ([b] (im-used nil b))
  ([h b] (response im-used-status h b)))

(defn insufficient-storage
  "Generate HTTP response document with status INSUFFICIENT STORAGE."
  ([b] (insufficient-storage nil b))
  ([h b] (response insufficient-storage-status h b)))

(defn internal-server-error
  "Generate HTTP response document with status INTERNAL SERVER ERROR."
  ([b] (internal-server-error nil b))
  ([h b] (response internal-server-error-status h b)))

(defn length-required
  "Generate HTTP response document with status LENGTH REQUIRED."
  ([b] (length-required nil b))
  ([h b] (response length-required-status h b)))

(defn locked
  "Generate HTTP response document with status LOCKED."
  ([b] (locked nil b))
  ([h b] (response locked-status h b)))

(defn loop-detected
  "Generate HTTP response document with status LOOP DETECTED."
  ([b] (loop-detected nil b))
  ([h b] (response loop-detected-status h b)))

(defn method-not-allowed
  "Generate HTTP response document with status METHOD NOT ALLOWED."
  ([b] (method-not-allowed nil b))
  ([h b] (response method-not-allowed-status h b)))

(defn misdirected-request
  "Generate HTTP response document with status MISDIRECTED REQUEST."
  ([b] (misdirected-request nil b))
  ([h b] (response misdirected-request-status h b)))

(defn moved-permanently
  "Generate HTTP response document with status MOVED PERMANENTLY."
  ([b] (moved-permanently nil b))
  ([h b] (response moved-permanently-status h b)))

(defn multi-status
  "Generate HTTP response document with status MULTI-STATUS."
  ([b] (multi-status nil b))
  ([h b] (response multi-status-status h b)))

(defn multiple-choices
  "Generate HTTP response document with status MULTIPLE CHOICES."
  ([b] (multiple-choices nil b))
  ([h b] (response multiple-choices-status h b)))

(defn network-authentication-required
  "Generate HTTP response document with status NETWORK AUTHENTICATION REQUIRED."
  ([b] (network-authentication-required nil b))
  ([h b] (response network-authentication-required-status h b)))

(defn no-content
  "Generate HTTP response document with status NO CONTENT."
  ([b] (no-content nil b))
  ([h b] (response no-content-status h b)))

(defn non-authoritative-information
  "Generate HTTP response document with status NON-AUTHORITATIVE INFORMATION."
  ([b] (non-authoritative-information nil b))
  ([h b] (response non-authoritative-information-status h b)))

(defn not-acceptable
  "Generate HTTP response document with status NOT ACCEPTABLE."
  ([b] (not-acceptable nil b))
  ([h b] (response not-acceptable-status h b)))

(defn not-extended
  "Generate HTTP response document with status NOT EXTENDED."
  ([b] (not-extended nil b))
  ([h b] (response not-extended-status h b)))

(defn not-found
  "Generate HTTP response document with status NOT FOUND."
  ([b] (not-found nil b))
  ([h b] (response not-found-status h b)))

(defn not-implemented
  "Generate HTTP response document with status NOT IMPLEMENTED."
  ([b] (not-implemented nil b))
  ([h b] (response not-implemented-status h b)))

(defn not-modified
  "Generate HTTP response document with status NOT MODIFIED."
  ([b] (not-modified nil b))
  ([h b] (response not-modified-status h b)))

(defn ok
  "Generate HTTP response document with status OK."
  ([b] (ok nil b))
  ([h b] (response ok-status h b)))

(defn partial-content
  "Generate HTTP response document with status PARTIAL CONTENT."
  ([b] (partial-content nil b))
  ([h b] (response partial-content-status h b)))

(defn payload-too-large
  "Generate HTTP response document with status PAYLOAD TOO LARGE."
  ([b] (payload-too-large nil b))
  ([h b] (response payload-too-large-status h b)))

(defn payment-required
  "Generate HTTP response document with status PAYMENT REQUIRED."
  ([b] (payment-required nil b))
  ([h b] (response payment-required-status h b)))

(defn permanent-redirect
  "Generate HTTP response document with status PERMANENT REDIRECT."
  ([b] (permanent-redirect nil b))
  ([h b] (response permanent-redirect-status h b)))

(defn precondition-failed
  "Generate HTTP response document with status PRECONDITION FAILED."
  ([b] (precondition-failed nil b))
  ([h b] (response precondition-failed-status h b)))

(defn precondition-required
  "Generate HTTP response document with status PRECONDITION REQUIRED."
  ([b] (precondition-required nil b))
  ([h b] (response precondition-required-status h b)))

(defn processing
  "Generate HTTP response document with status PROCESSING."
  ([b] (processing nil b))
  ([h b] (response processing-status h b)))

(defn proxy-authentication-required
  "Generate HTTP response document with status PROXY AUTHENTICATION REQUIRED."
  ([b] (proxy-authentication-required nil b))
  ([h b] (response proxy-authentication-required-status h b)))

(defn range-not-satisfiable
  "Generate HTTP response document with status RANGE NOT SATISFIABLE."
  ([b] (range-not-satisfiable nil b))
  ([h b] (response requested-range-not-satisfiable-status h b)))

(defn request-header-fields-too-large
  "Generate HTTP response document with status REQUEST HEADER FIELDS TOO LARGE."
  ([b] (request-header-fields-too-large nil b))
  ([h b] (response request-header-fields-too-large-status h b)))

(defn request-timeout
  "Generate HTTP response document with status REQUEST TIMEOUT."
  ([b] (request-timeout nil b))
  ([h b] (response request-timeout-status h b)))

(defn reset-content
  "Generate HTTP response document with status RESET CONTENT."
  ([b] (reset-content nil b))
  ([h b] (response reset-content-status h b)))

(defn see-other
  "Generate HTTP response document with status SEE OTHER."
  ([b] (see-other nil b))
  ([h b] (response see-other-status h b)))

(defn service-unavailable
  "Generate HTTP response document with status SERVICE UNAVAILABLE."
  ([b] (service-unavailable nil b))
  ([h b] (response service-unavailable-status h b)))

(defn switching-protocols
  "Generate HTTP response document with status SWITCHING PROTOCOLS."
  ([b] (switching-protocols nil b))
  ([h b] (response switching-protocols-status h b)))

(defn temporary-redirect
  "Generate HTTP response document with status TEMPORARY REDIRECT."
  ([b] (temporary-redirect nil b))
  ([h b] (response temporary-redirect-status h b)))

(defn too-many-requests
  "Generate HTTP response document with status TOO MANY REQUEST."
  ([b] (too-many-requests nil b))
  ([h b] (response too-many-requests-status h b)))

(defn uri-too-long
  "Generate HTTP response document with status URI TOO LONG."
  ([b] (uri-too-long nil b))
  ([h b] (response request-uri-too-long-status h b)))

(defn unauthorized
  "Generate HTTP response document with status UNAUTHORIZED."
  ([b] (unauthorized nil b))
  ([h b] (response unauthorized-status h b)))

(defn unavailable-for-legal-reasons
  "Generate HTTP response document with status UNAVAILABLE FOR LEGAL REASONS."
  ([b] (unavailable-for-legal-reasons nil b))
  ([h b] (response unavailable-for-legal-reasons-status h b)))

(defn unprocessable-entity
  "Generate HTTP response document with status UNPROCESSABLE ENTITY."
  ([b] (unprocessable-entity nil b))
  ([h b] (response unprocessable-entity-status h b)))

(defn unsupported-media-type
  "Generate HTTP response document with status UNSUPPORTED MEDIA TYPE."
  ([b] (unsupported-media-type nil b))
  ([h b] (response unsupported-media-type-status h b)))

(defn upgrade-required
  "Generate HTTP response document with status UPGRADE REQUIRED."
  ([b] (upgrade-required nil b))
  ([h b] (response upgrade-required-status h b)))

(defn use-proxy
  "Generate HTTP response document with status USE PROXY."
  ([b] (use-proxy nil b))
  ([h b] (response use-proxy-status h b)))

(defn variant-also-negotiates
  "Generate HTTP response document with status VARIANT ALSO NEGOTIATES."
  ([b] (variant-also-negotiates nil b))
  ([h b] (response variant-also-negotiates-status h b)))

;; status

(defn success?
  "Determine if HTTP response document d has status of OK or other success statuses (200 <= status <= 299)."
  [d]
  (when-let [s (get d :status)] (and (>= s ok-status) (<= s 299))))

(defn fail?
  "Determine if HTTP response document has status of non-success."
  [d]
  (not (success? d)))

(defn success-body
  "Extracts body from HTTP response document d iff the status is successful."
  [d]
  (when (success? d) (:body d)))

(def status-of-s
  "(defn $fn? \"Determines if HTTP response document d has $status status.\" [d] (status-of? d $fn-status))")

(defn status-of?
  "Determines if HTTP response document d "
  [d v]
  (= v (get d :status)))

(defn accepted? "Determines if HTTP response document d has ACCEPTED status." [d] (status-of? d accepted-status))
(defn already-reported? "Determines if HTTP response document d has ALREADY REPORTED status." [d] (status-of? d already-reported-status))
(defn bad-gateway? "Determines if HTTP response document d has BAD GATEWAY status." [d] (status-of? d bad-gateway-status))
(defn bad-request? "Determines if HTTP response document d has BAD REQUEST status." [d] (status-of? d bad-request-status))
(defn conflict? "Determines if HTTP response document d has CONFLICT status." [d] (status-of? d conflict-status))
(defn continue? "Determines if HTTP response document d has CONTINUE status." [d] (status-of? d continue-status))
(defn created? "Determines if HTTP response document d has CREATED status." [d] (status-of? d created-status))
(defn expectation-failed? "Determines if HTTP response document d has EXPECTATION FAILED status." [d] (status-of? d expectation-failed-status))
(defn failed-dependency? "Determines if HTTP response document d has FAILED DEPENDENCY status." [d] (status-of? d failed-dependency-status))
(defn forbidden? "Determines if HTTP response document d has FORBIDDEN status." [d] (status-of? d forbidden-status))
(defn found? "Determines if HTTP response document d has FOUND status." [d] (status-of? d found-status))
(defn gateway-timeout? "Determines if HTTP response document d has GATEWAY TIMEOUT status." [d] (status-of? d gateway-timeout-status))
(defn gone? "Determines if HTTP response document d has GONE status." [d] (status-of? d gone-status))
(defn http-version-not-supported? "Determines if HTTP response document d has HTTP VERSION NOT SUPPORTED status." [d] (status-of? d http-version-not-supported-status))
(defn im-a-teapot? "Determines if HTTP response document d has I'M A TEAPOT status." [d] (status-of? d im-a-teapot-status))
(defn im-used? "Determines if HTTP response document d has IM USED status." [d] (status-of? d im-used-status))
(defn insufficient-storage? "Determines if HTTP response document d has INSUFFICIENT STORAGE status." [d] (status-of? d insufficient-storage-status))
(defn internal-server-error? "Determines if HTTP response document d has INTERNAL SERVER ERROR status." [d] (status-of? d internal-server-error-status))
(defn length-required? "Determines if HTTP response document d has LENGTH REQUIRED status." [d] (status-of? d length-required-status))
(defn locked? "Determines if HTTP response document d has LOCKED status." [d] (status-of? d locked-status))
(defn loop-detected? "Determines if HTTP response document d has LOOP DETECTED status." [d] (status-of? d loop-detected-status))
(defn method-not-allowed? "Determines if HTTP response document d has METHOD NOT ALLOWED status." [d] (status-of? d method-not-allowed-status))
(defn misdirected-request? "Determines if HTTP response document d has MISDIRECTED REQUEST status." [d] (status-of? d misdirected-request-status))
(defn moved-permanently? "Determines if HTTP response document d has MOVED PERMANENTLY status." [d] (status-of? d moved-permanently-status))
(defn multi-status? "Determines if HTTP response document d has MULTI-STATUS status." [d] (status-of? d multi-status-status))
(defn multiple-choices? "Determines if HTTP response document d has MULTIPLE CHOICES status." [d] (status-of? d multiple-choices-status))
(defn network-authentication-required? "Determines if HTTP response document d has NETWORK AUTHENTICATION REQUIRED status." [d] (status-of? d network-authentication-required-status))
(defn no-content? "Determines if HTTP response document d has NO CONTENT status." [d] (status-of? d no-content-status))
(defn non-authoritative-information? "Determines if HTTP response document d has NON-AUTHORITATIVE INFORMATION status." [d] (status-of? d non-authoritative-information-status))
(defn not-acceptable? "Determines if HTTP response document d has NOT ACCEPTABLE status." [d] (status-of? d not-acceptable-status))
(defn not-extended? "Determines if HTTP response document d has NOT EXTENDED status." [d] (status-of? d not-extended-status))
(defn not-found? "Determines if HTTP response document d has NOT FOUND status." [d] (status-of? d not-found-status))
(defn not-implemented? "Determines if HTTP response document d has NOT IMPLEMENTED status." [d] (status-of? d not-implemented-status))
(defn not-modified? "Determines if HTTP response document d has NOT MODIFIED status." [d] (status-of? d not-modified-status))
(defn ok? "Determines if HTTP response document d has OK status." [d] (status-of? d ok-status))
(defn partial-content? "Determines if HTTP response document d has PARTIAL CONTENT status." [d] (status-of? d partial-content-status))
(defn payload-too-large? "Determines if HTTP response document d has PAYLOAD TOO LARGE status." [d] (status-of? d payload-too-large-status))
(defn payment-required? "Determines if HTTP response document d has PAYMENT REQUIRED status." [d] (status-of? d payment-required-status))
(defn permanent-redirect? "Determines if HTTP response document d has PERMANENT REDIRECT status." [d] (status-of? d permanent-redirect-status))
(defn precondition-failed? "Determines if HTTP response document d has PRECONDITION FAILED status." [d] (status-of? d precondition-failed-status))
(defn precondition-required? "Determines if HTTP response document d has PRECONDITION REQUIRED status." [d] (status-of? d precondition-required-status))
(defn processing? "Determines if HTTP response document d has PROCESSING status." [d] (status-of? d processing-status))
(defn proxy-authentication-required? "Determines if HTTP response document d has PROXY AUTHENTICATION REQUIRED status." [d] (status-of? d proxy-authentication-required-status))
(defn range-not-satisfiable? "Determines if HTTP response document d has RANGE NOT SATISFIABLE status." [d] (status-of? d requested-range-not-satisfiable-status))
(defn request-header-fields-too-large? "Determines if HTTP response document d has REQUEST HEADER FIELDS TOO LARGE status." [d] (status-of? d request-header-fields-too-large-status))
(defn request-timeout? "Determines if HTTP response document d has REQUEST TIMEOUT status." [d] (status-of? d request-timeout-status))
(defn reset-content? "Determines if HTTP response document d has RESET CONTENT status." [d] (status-of? d reset-content-status))
(defn see-other? "Determines if HTTP response document d has SEE OTHER status." [d] (status-of? d see-other-status))
(defn service-unavailable? "Determines if HTTP response document d has SERVICE UNAVAILABLE status." [d] (status-of? d service-unavailable-status))
(defn switching-protocols? "Determines if HTTP response document d has SWITCHING PROTOCOLS status." [d] (status-of? d switching-protocols-status))
(defn temporary-redirect? "Determines if HTTP response document d has TEMPORARY REDIRECT status." [d] (status-of? d temporary-redirect-status))
(defn too-many-requests? "Determines if HTTP response document d has TOO MANY REQUESTS status." [d] (status-of? d too-many-requests-status))
(defn uri-too-long? "Determines if HTTP response document d has URI TOO LONG status." [d] (status-of? d request-uri-too-long-status))
(defn unauthorized? "Determines if HTTP response document d has UNAUTHORIZED status." [d] (status-of? d unauthorized-status))
(defn unavailable-for-legal-reasons? "Determines if HTTP response document d has UNAVAILABLE FOR LEGAL REASONS status." [d] (status-of? d unavailable-for-legal-reasons-status))
(defn unprocessable-entity? "Determines if HTTP response document d has UNPROCESSABLE ENTITY status." [d] (status-of? d unprocessable-entity-status))
(defn unsupported-media-type? "Determines if HTTP response document d has UNSUPPORTED MEDIA TYPE status." [d] (status-of? d unsupported-media-type-status))
(defn upgrade-required? "Determines if HTTP response document d has UPGRADE REQUIRED status." [d] (status-of? d upgrade-required-status))
(defn use-proxy? "Determines if HTTP response document d has USE PROXY status." [d] (status-of? d use-proxy-status))
(defn variant-also-negotiates? "Determines if HTTP response document d has VARIANT ALSO NEGOTIATES status." [d] (status-of? d variant-also-negotiates-status))

(defn not-ok? "Determine if HTTP response document d is not OK status." [d] (not (ok? d)))

(defn <-status
  "Extracts status from document d."
  [d & [s]]
  (get d :status s))

;; body

(defn <-body
  "Extracts the body of x recursively.
  If the body is an HTTP response (contains :body), then recursively extract body."
  [x]
  (if-some [b (get x :body)]
    (<-body b)
    x))

(defn unwrap
  "Unwraps the body of x recursively."
  [x]
  (<-body x))

(defn echo
  "Echoes the request to response.
  Used mostly for development sanity checks."
  [r]
  (let [nb (body->map r)]
    (body->text r (ok nb))))

;;; macros

(defmacro assert-args
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                  (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
       ~(let [more (nnext pairs)]
          (when more
            (list* `assert-args more)))))

(defmacro if-let-ok
  "bindings => binding-form test
  If test is true, evaluates then with binding-form bound to the value of
  test, if not, yields else"
  {:added "1.0"}
  ([bindings then]
   `(if-let-ok ~bindings ~then nil))
  ([bindings then else & oldform]
   (assert-args
     (vector? bindings) "a vector for its binding"
     (nil? oldform) "1 or 2 forms after binding vector"
     (= 2 (count bindings)) "exactly 2 forms in binding vector")
   (let [form (bindings 0) tst (bindings 1)]
     `(let [temp# ~tst]
        (if (ok? temp#)
          (let [~form temp#]
            ~then)
          (let [~form temp#]
            ~else))))))

(defmacro when-let-ok
  "bindings => binding-form test
   When test ok, evaluates body with binding-form bound to the
   value of test"
  {:added "1.6"}
  [bindings & body]
  (assert-args
    (vector? bindings) "a vector for its binding"
    (= 2 (count bindings)) "exactly 2 forms in binding vector")
  (let [form (bindings 0) tst (bindings 1)]
    `(let [temp# ~tst]
       (if (not-ok? temp#)
         nil
         (let [~form temp#]
           ~@body)))))

(defmacro when-let-not-ok
  "bindings => binding-form test
   When test not ok, evaluates body with binding-form bound to the
   value of test"
  {:added "1.6"}
  [bindings & body]
  (assert-args
    (vector? bindings) "a vector for its binding"
    (= 2 (count bindings)) "exactly 2 forms in binding vector")
  (let [form (bindings 0) tst (bindings 1)]
    `(let [temp# ~tst]
       (if (ok? temp#)
         nil
         (let [~form temp#]
           ~@body)))))

(defn lift-custom
  "Lifts a function f with options arguments args using options map to handle processing.

  options map:

  {:non-nil-response-status :$valid-http-status-code ; defaults to 200 (OK)
   :result-handler-f :$function-with-result-options-f-args-parameters ; if none, pass thru non-nil result as :body
   :nil-response-status :$valid-http-status-code ; defaults to 404 (Not Found)
   :nil-response-body :$scalar-value-for-nil-responses ; if this is non-nil, uses this and ignores :nil-result-handler-f
   :nil-result-handler-f :$function-with-options-f-args-parameters ; if none, sets :body in response to nil
   :exception-status :$valid-http-status-code ; defaults to 500 (Internal Server Error)
   :exception-body-f :$function-with-exception-options-f-args ; defaults to distilled exception map ->exception-info
   }

  Example functions:

  :result-handler-f

  (fn [r & _] (str \"Converts result to string and ignores options, f, and args: \" r))
  (fn [r options f args] {:result r :options options :f f :args args})

  :nil-result-handler-f

  (fn [& _] \"Result is nil.\") ; same as using the :nil-response-body \"Result is nil.\" in options
  (fn [options f args] {:options options :f f :args args :message \"Result was nil.\"})

  :exception-body-f

  (fn [e & _] (.getMessage e)) ; just sets response :body to exception message
  (fn [e options f args] (str \"Exception occurred with options: \" options \", f \" f \", args \" args \" [\" e \"]))
  "
  [options f & args]
  (try
    (if-let [r (apply f args)]
      (response (get options :non-nil-response-status ok-status)
                (if-let [rhf (get options :result-handler-f)]
                  (rhf r options f args)
                  r))
      (response (get options :nil-response-status not-found-status)
                (if-let [nrb (get options :nil-response-body)]
                  nrb
                  (when-let [nrhf (get options :nil-result-handler-f)]
                    (nrhf options f (vec args))))))
    (catch Exception e
      (response (get options :exception-status internal-server-error-status)
                ((get options :exception-body-f ->exception-info) e options f args)))))

(def lift
  "Lifts function to HTTP response with default options."
  (partial lift-custom nil))

;; developer

(def reference-sample-ring-request {:ssl-client-cert nil,
                                    :cookies {},
                                    :remote-addr "127.0.0.1",
                                    :params {},
                                    :flash nil,
                                    :route-params {},
                                    :headers {"upgrade-insecure-requests" "1",
                                              "accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                                              "connection" "keep-alive",
                                              "dnt" "1",
                                              "accept-encoding" "gzip, deflate, sdch",
                                              "user-agent" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.93 Safari/537.36",
                                              "accept-language" "en-US,en;q=0.8",
                                              "host" "localhost:3000"},
                                    :server-port 3000,
                                    :content-length nil,
                                    :form-params {},
                                    :session/key nil,
                                    :query-params {},
                                    :content-type nil,
                                    :character-encoding nil,
                                    :uri "/echo/request",
                                    :server-name "localhost",
                                    :query-string nil,
                                    :multipart-params {},
                                    :scheme :http,
                                    :request-method :get,
                                    :session {}})
