(ns alephy.core
  (:import
   [io.netty.handler.ssl SslContextBuilder])
  (:require
   [compojure.core :as compojure :refer [GET]]
   [ring.middleware.params :as params]
   [compojure.route :as route]
   [compojure.response :refer [Renderable]]
   [aleph.http :as http]
   [byte-streams :as bs]
   [manifold.stream :as s]
   [manifold.deferred :as d]
   [clojure.core.async :as a]
   [clojure.java.io :refer [file]]))

(defn hello-world-handler
  [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "hello world!"})

;; A non-standard response handler which returns a deferred which yields
;; a Ring response after one second. In a typical Ring-compliant server,
;; this would require holding onto a thread via Thread.sleep() or a
;; similar mechanism, but the use of a deferred allows for the thread to
;; be immediately released without an immediate response.

;; This is an atypical usage of manifold.deferred/timeout!, which puts
;; a 'timeout value' into a deferred after an interval if it hasn't
;; already been realized. Here there's nothing else trying to touch
;; the deferred, so it will simply yield the 'hello world' response
;; after 1000 milliseconds.

(defn delayed-hello-world-handler
  [req]
  (d/timeout!
   (d/deferred)
   1000
   (hello-world-handler req)))

;; Compojure will normally dereference deferreds and return the
;; realized value. Unfortunately, this blocks the thread. Since Aleph
;; can accept the unrealized deferred, we extend Compojure's
;; Renderable protocol to pass the deferred through unchanged so it
;; can be handled asynchronously.

(extend-protocol Renderable
  manifold.deferred.IDeferred
  (render [d _] d))

;; Alternately, we can use a core.async goroutine to create our
;; response, and convert the channel it returns using
;; manifold.deferred/->source, and then take the first message from
;; it. This is entirely equivalent to the previous implementation.

(defn delayed-hello-world-handler
  [req]
  (s/take!
   (s/->source
    (a/go
      (let [_ (a/<! (a/timeout 1000))]
        (hello-world-handler req))))))

;; Returns a streamed HTTP response, consisting of newline-delimited
;; numbers every 100 milliseconds. While this would typically be
;; represented by a lazy sequence, instead we use a Manifold
;; stream. Similar to the use of the deferred above, this means we
;; don't need to allocate a thread per-request.

;; In this handler we're assuming the string value for count is a
;; valid number. If not, Integer.parseInt() will throw an exception,
;; and we'll return a 500 status response with the stack trace. If we
;; wanted to be more precise in our status, we'd wrap the parsing code
;; with a try/catch that returns a 400 status when the count is
;; malformed.

;; manifold.stream/periodically is similar to Clojure's repeatedly,
;; except that it emits the value returned by the function at a fixed
;; interval.

;; (defn streaming-numbers-handler
;;   [{:keys [params]}]
;;   (let [cnt (Integer/parseInt (get params "count" "0"))]
;;     {:status 200
;;      :headers {"content-type" "text/plain"}
;;      :body (let [sent (atom 0)]
;;              (->> (s/periodically 100 #(str (swap! sent inc) "\n"))
;;                   (s/transform (take cnt))))}))


;; (defn streaming-numbers-handler
;;   [{:keys [params]}]
;;   (let [cnt (Integer/parseInt (get params "count" "0"))]
;;     {:status 200
;;      :headers {"content-type" "text/plain"}
;;      :body (->> (range cnt)
;;                 (map #(do (Thread/sleep 100) %))
;;                 (map #(str % "\n")))}))


(defn streaming-numbers-handler
  [{:keys [params]}]
  (let [cnt (Integer/parseInt (get params "count" "0"))
        body (a/chan)]
    ;; create a goroutine that emits incrementing numbers once every 100 milliseconds
    (a/go-loop [i 0]
      (if (< i cnt)
        (let [_ (a/<! (a/timeout 100))]
          (a/>! body (str i "\n"))
          (recur (inc i)))
        (a/close! body)))
    ;; return a response containing the coerced channel as the body
    {:status 200
     :headers {"content-type" "text/plain"}
     :body (s/->source body)}))

(def handler
  (params/wrap-params
   (compojure/routes
    (GET "/hello"         [] hello-world-handler)
    (GET "/delayed_hello" [] delayed-hello-world-handler)
    (GET "/numbers"       [] streaming-numbers-handler)
    (route/not-found "No such page."))))

#_(def s (http/start-server handler {:port 10000}))
#_(.close s)
