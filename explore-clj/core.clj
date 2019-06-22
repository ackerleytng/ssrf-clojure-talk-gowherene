(ns explore-ssrf.core
  (:require [clojure.java.io :as io]
            [etaoin.api :as e]
            [clj-http.client :as client]
            [org.httpkit.client :as http])
  (:import [java.net URL URI InetAddress]
           [java.io StringWriter InputStreamReader])
  (:gen-class))

;; Define driver paths

(def chromedriver-path
  (.getCanonicalPath (io/file "./drivers/chromedriver")))

(def geckodriver-path
  (.getCanonicalPath (io/file "./drivers/geckodriver")))

;; Convenience functions

(defn etaoin-get [driver-path url]
  (let [driver (e/chrome {:path-driver driver-path})]
    (try
      (doto driver
        (e/go url)
        (e/wait 5))
      (catch Exception ex
        (ex-data ex))
      (finally
        (e/quit driver)))))

(defn clj-http-get [url]
  (try
    (client/get url)
    (catch Exception ex
      ex)))'

;; ===========================================================
;;   Overview of code dive
;;   1. Explore host injection
;;   2. Explore port injection
;;   3. Use port injection to understand implementations of
;;      3a. http-kit
;;      3b. clj-http
;;   4. Explore path injection across java versions
;;   5. Show issue that extends into clojure
;; ===========================================================

;; -----------------------------------------------------------
;;   1. Start exploration here: host injection
;; -----------------------------------------------------------

(def host-injection-url "http://wikipedia.org#@t.co/")

;; Try out host injection on the first thing I'd reach for

(comment

  (slurp host-injection-url)

  )

;; slurp actually parses out wikipedia.org and accesses wikipedia.org
;; Response is empty because wikipedia.org didn't respond with any bites
;; Confirmed this with tcpdump
;; tcpdump -nn -vv '(port 80 or port 443) and host wikipedia.org'

;; As additional proof that slurp did not eat up the data somehow,
;;   google actually does respond to this malformed url

(comment

  (->> (slurp "http://google.com#@t.co/")
       (re-find #"<title>.*?</title>"))

  )

;; slurp's summarized implementation

(defn slurp-summarized [url]
  (let [sw (StringWriter.)]
    (with-open
      [r (InputStreamReader. (.openStream (URL. url)))]
      (io/copy r sw)
      (.toString sw))))

(comment

  (slurp-summarized host-injection-url)

  (->> (slurp-summarized "http://google.com")
       (re-find #"<title>.*?</title>" ))

  )

;; Try out some common http client libraries
;; + clj-http: An idiomatic clojure http client wrapping the apache client
;; + etaoin: Pure Clojure Webdriver protocol implementation
;; + http-kit: minimalist, high-performance Clojure HTTP server/client library

(comment

  (client/parse-url host-injection-url)

  (->> (client/get host-injection-url)
       :body
       (re-find #"(?i)<title>.*?</title>"))

  (etaoin-get chromedriver-path host-injection-url)

  (etaoin-get geckodriver-path host-injection-url)

  (->> @(http/get host-injection-url)
       :body
       (re-find #"(?i)<title>.*?</title>"))

  )

;; Doesn't seem to have the same problem as php and curl in php

;; -----------------------------------------------------------
;;   2. Port injection?
;; -----------------------------------------------------------

(def port-injection-url "http://www.google.com:443:80/")

;; Try out port injection on slurp

(comment

  (try
    (slurp port-injection-url)
    (catch Exception e
      e))

  )

;; FileNotFoundException ?!

;; >> Do some sleuthing here

;; slurp first tries to interpret the string as a URL.
;;   if it fails, it'll try to interpret the string as a File.

(comment

  (clj-http-get port-injection-url)

  (etaoin-get chromedriver-path port-injection-url)

  (etaoin-get geckodriver-path port-injection-url)

  (http/get port-injection-url)

  ;; Host is null??

  )

;; -----------------------------------------------------------
;;   3a. Parsing implementation in http-kit
;; -----------------------------------------------------------

;; Dug into http-kit (from backtrace) to find that it uses java.net's URI,
;;   instead of URL to parse addresses
;; java.net.URL's equals() method blocks because it goes out to the network
;;   to do a reverse lookup of the hostname
;;   http://blog.markfeeney.com/2010/11/java-uri-vs-url.html

(comment

  ;; Returns nil as expected
  (.getHost (URI. port-injection-url))

  )

;; Good that http-kit checks for null hosts, because without additional configuration,
;;   http-kit will use InetAddress's static method getByName to resolve hosts,
;;   and (getByName nil) returns localhost!!

(comment

  (InetAddress/getByName (.getHost (URI. "http://www.google.com")))

  (InetAddress/getByName nil)

  (InetAddress/getByName (.getHost (URI. port-injection-url)))

  )

;; -----------------------------------------------------------
;;   3b. Parsing implementation in clj-http
;; -----------------------------------------------------------

(comment

  (client/get port-injection-url)

  )

;; >> Look at the backtrace to examine how the url is parsed

(comment

  (client/parse-url port-injection-url)

  (URL. port-injection-url)

  )

;; slurp and clj-http's get both use java's URL. to parse the provided URL

;; -----------------------------------------------------------
;;   4. Going back to path injection, across java versions
;; -----------------------------------------------------------

;; Coded up a short java program to test for the path-injection vulnerability
;;   (explore-java/Main.java)
;; Tried it on these docker containers but didn't find any discrepancy
;;   like that of php and curl in php

;; docker run --rm --volume $PWD/:/src --workdir /src openjdk:8-alpine sh -c 'javac Main.java && java Main https://wikipedia.org#@t.co 10'

;; openjdk:7u91-alpine => SSL error
;; openjdk:7-alpine
;; openjdk:8u92-alpine
;; openjdk:8-alpine
;; openjdk:12-alpine

;; Dug around in https://github.com/orangetw/Tiny-URL-Fuzzer for clues on why
;;   Orange Tsai considered Java's net.URL to be vulnerable,
;;   found note about "weird" parsing in java
;; Urls had more than 1 consecutive @s in the string, like
;;   https://wikipedia.org@@t.co

;; docker run --rm --volume $PWD/:/src --workdir /src openjdk:8-alpine sh -c 'javac Main.java && java Main https://wikipedia.org@@t.co 10'

;; openjdk:7u91-alpine => host = @t.co
;; openjdk:7-alpine => couldn't parse out host
;; openjdk:8u92-alpine => host = @t.co
;; openjdk:8-alpine => couldn't parse out host
;; openjdk:12-alpine => couldn't parse out host

;; -----------------------------------------------------------
;;   5. Naturally, this issue is propagated to clojure
;; -----------------------------------------------------------

(comment

  ;; Parsing a legit url (dashes in domain names are allowed: https://css-tricks.com)
  (client/parse-url
   "https://username:password@server-name:8000/uri?query=string&is=long")

  (System/getProperty "java.version")

  (client/parse-url "https://wikipedia.org@@t.co")

  )
