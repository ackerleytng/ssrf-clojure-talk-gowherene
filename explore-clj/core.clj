(ns explore-ssrf.core
  (:require [clojure.java.io :as io]
            [etaoin.api :as e]
            [clj-http.client :as client]
            [org.httpkit.client :as http])
  (:import [java.net URL URI]
           [java.net InetAddress])
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
      ex)))

;; -----------------------------------------------------------
;;   Start exploration here: host injection
;; -----------------------------------------------------------

(def host-injection-url "http://wikipedia.org#@t.co/")

;; Try out host injection on the first thing I'd reach for

(comment

  (slurp host-injection-url)  ;; <= TODO check why it retrieves ""

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
;;   Port injection?
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
;;   Let's dig into clj-http a little bit
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
;;   Look into http/get too
;; -----------------------------------------------------------

(comment

  (http/get port-injection-url)

  )

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
;;   Going back to path injection
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

;; Naturally, this issue is propagated to clojure

(comment

  ;; Parsing a legit url (dashes in domain names are allowed: https://css-tricks.com)
  (client/parse-url
   "https://username:password@server-name:8000/uri?query=string&is=long")

  (System/getProperty "java.version")

  (client/parse-url "https://wikipedia.org@@t.co")

  )
