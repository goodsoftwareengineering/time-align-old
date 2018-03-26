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

(declare .toLocaletimeString)
(defn ->locale-time-string
  [x]
  (.toLocaleTimeString x))

(declare .getHours)
(declare .getMinutes)
(defn ->time-string-relaxed [x]
  (let [hours (str (.getHours x))
        minutes (str (.getMinutes x))
        hours-with-padded-zeros (if (= 1 (count hours))
                                  (str "0" hours) (str hours))
        minutes-with-padded-zeros (if (= 1 (count minutes))
                                  (str "0" minutes) (str minutes))]
    (str hours-with-padded-zeros ":" minutes-with-padded-zeros)))

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

(declare .addEventListener)
(defn add-event-listener [element event fn]
  (.addEventListener element event fn))

(defn user-agent []
  (.-userAgent js/navigator))

(defn pi []
  (.-PI js/Math))

(declare .toFixed)
(defn round-decimals [n d]
  (.toFixed n d))
