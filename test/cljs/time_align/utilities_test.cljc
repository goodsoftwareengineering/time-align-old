(ns time-align.utilities-test
  (:require
    #?(:cljs [cljs.test :as t :refer-macros [is are deftest testing]]
        :clj [clojure.test :as t :refer [is are deftest testing]])
    #?(:clj [java-time :as time])
    #?(:cljs [pjstadig.humane-test-output])
    [time-align.utilities :as rc]))

(deftest test-utils
  (testing "One week from now should add 604800000"
    (is (= (rc/one-week-from-now (rc/make-date 1970 1 1))
           604800000
           rc/week-ms)))


  (testing "One week ago should subtract 604800000"
    (is (= (rc/one-week-ago (rc/make-date 1970 1 8))
           0)))


  (testing "Start of today should respect timezones"
    #?(:cljs (let [tz-string "America/Los_Angeles"
                   date      (js/Date. 2017 9 21 0 0 0 0)]
               (is (= (.valueOf date)
                      (.valueOf (rc/start-of-today date (rc/get-default-timezone))))))
       :clj  (let [tz-string "America/Los_Angeles"
                   date      (time/with-zone-same-instant (time/zoned-date-time 2017 9 21 0 0 0 0 (time/zone-id tz-string))
                                                          (time/zone-id tz-string))]
               (is (= (time/instant date)
                      (time/instant (rc/start-of-today date tz-string)))))))


  (testing "End of today should respect the end of day in the timezone of the user"
    #?(:cljs (let [tz-string "America/Los_Angeles"
                   date      (js/Date. 2017 9 21 3 5 0 0)]
               (is (< (.valueOf date)
                      (.valueOf (rc/end-of-today date (rc/get-default-timezone))))))
       :clj (let [tz-string "America/Los_Angeles"
                  date      (time/with-zone-same-instant (time/zoned-date-time 2017 9 21 3 5 0 0 (time/zone-id tz-string))
                                                         (time/zone-id tz-string))]
              (is (time/before? date (rc/end-of-today date tz-string)))))))
