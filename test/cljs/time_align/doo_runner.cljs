(ns time-align.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [time-align.core-test]
            [time-align.handlers-test]
            [time-align.utilities-test]))

(doo-tests 'time-align.core-test
           'time-align.handlers-test
           'time-align.utilities-test)

