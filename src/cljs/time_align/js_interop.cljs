(ns time-align.js-interop)

(declare .valueOf)
(defn value-of
  [x]
  (.valueOf x))

(declare .stopPropagation)
(defn stop-propagation
  [x]
  (.stopPropagation x))

(declare .preventDefault)
(defn prevent-default
  [x]
  (.preventDefault x))

(declare .toTimeString)
(defn ->time-string
  [x]
  (.toTimeString x))

(declare .toDateString)
(defn ->date-string
  [x]
  (.toDateString x))

(declare .toISOString)
(defn ->iso-string
  [x]
  (.toISOString x))

(declare .back)
(defn back!
  [x]
  (.back x))

(declare .getElementById)
(defn get-element-by-id
  [id]
  (.getElementById js/document id))

(declare .getElementsByClassName)
(defn get-elements-by-class-name
  [class-name]
  (.getElementsByClassName js/document class-name))

(declare .click)
(defn click!
  [x]
  (.click x))

(defn user-agent []
  (.-userAgent js/navigator))
