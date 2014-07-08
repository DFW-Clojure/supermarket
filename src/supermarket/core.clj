(ns supermarket.core
  (:require [clojure.core.async
             :as async
             :refer [go go-loop >! <! <!! >!! chan onto-chan timeout
                     dropping-buffer close!]]
            [incanter.core :refer [view]]
            [incanter.charts :refer [scatter-plot]]))

(def num-boxes 5)
(def items-per-box 100)
(def num-aisles 5)
(def shelves-per-aisle 3)

(def simulation-scale (/ 20))

(def box-search-time   (* 1000 simulation-scale))
(def aisle-travel-time (* 1000 simulation-scale))
(def shelf-stock-time  (* 1000 simulation-scale))

(def stockers-per-box   1)
(def stockers-per-aisle 2)
(def stockers-per-shelf 1)

(defprotocol Stockable
  (stock [location] [location item]))

(defprotocol Unpackable
  (unpack [container]))

(defprotocol Scarce
  (initialize [resource])
  (claim [resource task]))

(defrecord Request [task response])

(defn make-scarce [type concurrent-users requests]
  (extend type
    Scarce
    {:initialize
     (fn [resource]
       (doseq [_ (range concurrent-users)]
         (go-loop []
           (when-let [{:keys [task response]} (<! (requests resource))]
             (if-let [result (<! (task))]
               (>! response result)
               (close! response))
             (recur))))
       resource)

     :claim
     (fn [resource task]
       (go (let [response (chan)]
             (>! (requests resource) (Request. task response))
             (<! response))))}))

;; Boxes
(defrecord Box [items requests]
  Unpackable
  (unpack [box]
    (claim box
           #(go (when-let [item (<! items)]
                  (<! (timeout box-search-time))
                  item)))))

(make-scarce Box stockers-per-box :requests)

(defn box-with [contents]
  (let [items (chan)]
    (onto-chan items contents)
    (initialize (Box. items (chan)))))

(defrecord Item [aisle shelf])

(defn make-item []
  (Item. (rand-int num-aisles)
         (rand-int shelves-per-aisle)))

(defn make-box []
  (box-with (repeatedly items-per-box make-item)))

;; Shelves
(defrecord Shelf [items requests]
  Stockable
  (stock [shelf item]
    (claim shelf
           #(go (when item
                  (<! (timeout shelf-stock-time))
                  (>! items item)
                  item)))))

(make-scarce Shelf stockers-per-shelf :requests)

(defn make-shelf []
  (let [items (chan (dropping-buffer 0))]
    (initialize (Shelf. items (chan)))))

;; Aisles
(defrecord Aisle [shelves requests]
  Stockable
  (stock [aisle item]
    (claim aisle
           #(go (when-let [shelf (shelves (:shelf item))]
                  (<! (timeout aisle-travel-time))
                  (<! (stock shelf item))
                  item)))))

(make-scarce Aisle stockers-per-aisle :requests)

(defn make-aisle []
  (initialize (Aisle. (vec (repeatedly shelves-per-aisle make-shelf))
                      (chan))))

;; Stockers
(defn make-stocker [supermarket]
  (go-loop []
    (when-let [item (<! (unpack supermarket))]
      (<! (stock supermarket item))
      (recur))))

;; Supermarket
(defrecord Supermarket [aisles boxes num-stockers requests]
  Unpackable
  (unpack [supermarket]
    (go-loop [remaining-boxes (shuffle boxes)]
      (when-let [current-box (peek remaining-boxes)]
        (if-let [item (<! (unpack current-box))]
          item
          (recur (pop remaining-boxes))))))

  Stockable
  (stock [supermarket item]
    (stock (aisles (:aisle item)) item))

  (stock [supermarket]
    (claim supermarket
           #(go (let [stockers (repeatedly num-stockers
                                           (fn [] (make-stocker supermarket)))]
                  (<! (async/into [] (async/merge stockers)))
                  :done)))))

(make-scarce Supermarket 1 :requests)

(defn make-supermarket [num-stockers]
  (initialize (Supermarket. (vec (repeatedly num-aisles make-aisle))
                            (set (repeatedly num-boxes make-box))
                            num-stockers (chan))))

;; Simulation
(defn simulate-stocking [num-stockers]
  (let [supermarket (make-supermarket num-stockers)
        start (System/nanoTime)
        _ (<!! (stock supermarket))
        end (System/nanoTime)]
    (double (/ (- end start) 1000000000 simulation-scale))))

(defn run-simulation [max-stockers]
  (let [x (map inc (range max-stockers))
        y (pmap simulate-stocking x)]
    {:x x :y y}))

(defn plot [{:keys [x y]}]
  (view (scatter-plot x y
                      :x-label "Number of Stockers"
                      :y-label "Stock Time (s)")))

(def runs (future (run-simulation 40)))
;; (plot @runs)
