(ns supermarket.core
  (:require [clojure.core.async :as async :refer [go go-loop >! <! <!! >!!
                                                  chan onto-chan timeout]]))

(def num-shelves 3)
(def num-aisles 3)
(def num-boxes 2)
(def stockers-per-aisle 2)
(def stockers-per-shelf 1)
(def items-per-box 10)
(def stockers-per-box 1)

(defn make-box
  [items-in-box]
  (let [items   (chan items-per-box)
        acquire (chan)
        release (chan)]
    (onto-chan items items-in-box)
    (go-loop []
      (>! acquire 1)
      (<! release)
      (recur))
    {:acquire acquire
     :release release
     :items   items}))

(def supermarket
  (atom {:boxes (apply hash-map
                       (for [i (range num-boxes)]
                         [i (make-box (range 10))]))}))

(defn remove-box
  [box-index]
  (swap! supermarket update-in [:boxes] dissoc box-index)
  (println "removed box: " box-index))

(defn make-person
  []
  (go-loop []
    (when-let [[box-index {:keys [acquire release items]}]
               (-> @supermarket
                   :boxes
                   vec
                   rand-nth)]
      (<! acquire)
      (if-let [item (<! items)]
        (do
          (<! (timeout 1000))
          (println "retrieved item: " item))
        (remove-box box-index))
      (>! release 1))
    (recur)))
