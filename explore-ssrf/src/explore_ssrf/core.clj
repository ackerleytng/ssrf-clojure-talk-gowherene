(ns explore-ssrf.core
  (:require [clojure.java.io :as io]
            [etaoin.api :as e]
            [clj-http.client :as client]
            [org.httpkit.client :as http])
  (:import [java.net URL])
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
        (e/wait 1))
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
;;   Start exploration here
;; -----------------------------------------------------------

(def port-injection-url "http://127.0.0.1:11211:80/")

;; Try out port injection on the first thing you'd reach for

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

;; Try out some common http client libraries
;; + clj-http: An idiomatic clojure http client wrapping the apache client
;; + etaoin: Pure Clojure Webdriver protocol implementation
;; + http-kit: minimalist, high-performance Clojure HTTP server/client library

(comment

  (clj-http-get port-injection-url)

  (etaoin-get chromedriver-path port-injection-url)

  (etaoin-get geckodriver-path port-injection-url)

  (http/get port-injection-url)  ;; <== Does some parsing of it's own, should take a look

  )

;; Let's dig into clj-http a little bit

(comment

  (client/get port-injection-url)

  )

;; >> Look at the backtrace to examine how the url is parsed

(comment

  (client/parse-url port-injection-url)

  (URL. port-injection-url)

  )

;; >> Look into clj-http's code to examine how URL. is used

;; slurp and clj-http's get both use java's URL. to parse the provided URL
;; clj-http's parse-url provides the information in a nice dictionary form

(def host-injection-url "http://google.com#@evil.com/")

(comment

  (client/parse-url host-injection-url)

  (client/get host-injection-url)

  )
