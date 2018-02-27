(ns time-align.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [time-align.core-test]))

(doo-tests 'time-align.core-test)

