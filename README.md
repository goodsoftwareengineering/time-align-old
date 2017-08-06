# time-align

Life is about how time is spent. Use this tool to create a goal for how to spend time, and record how time is actually spent. Work towards aligning those two tracks.  

generated using Luminus version "2.9.11.46"

## Prerequisites
- [Vagrant][1]
- [VirtualBox][2]

[1]: https://www.vagrantup.com/
[2]: https://www.virtualbox.org/wiki/VirtualBox

## Running

- clone repo
- run `vagrant up`
- run `bash start.sh` (will open an ssh connection to vm)
- run `./cider-deps-repl` (in vm, will start an nrepl)
- connect to nrepl using cider at port 7000
- run `(start)` (launches the server)
- run `(start-fw)` (transpiles cljs and starts figwheel server)

## checklist for Proof Of Concept
- [ ] full crud interface for time-align
  - [x] structure in db.clj
  - [ ] periods
    - [ ] create new
    - [ ] read
      - [x] planned/actual wheel display
      - [ ] queue (any without start/stop)
    - [x] update
      - [ ] slidding
      - [ ] stretching/shrinking
    - [ ] delete
  - [ ] categories
    - [ ] create 
      - [ ] name
      - [ ] color
      - [ ] priority?
    - [ ] read 
    - [ ] update
    - [ ] delete
  - [ ] tasks
    - [ ] create
      - [ ] assign category (color + name)
      - [ ] meta data (name, desc, completed, dependencies)
      - [ ] priority
    - [ ] read
      - [ ] task list 
        - [ ] one list
        - [ ] sorting
          - [ ] date created
          - [ ] last modified
          - [ ] number of periods
          - [ ] category
          - [ ] priority
    - [ ] update
      - [ ] edit all data (can't delete unless no periods)
    - [ ] delete

## misc
### css solution garden
https://blog.estimate-work.com/a-new-world-writing-css-in-clojurescript-and-life-after-sass-bdf5bc80a24f
### linked code to save
(s/def ::un-linked-db (s/keys :req-un [::user ::tasks ::view ::categories]))
;; all category-id keys in :tasks match a category in :categories
(s/def ::task-category-links ;; no generator, this is a supportting predicate
  (fn [db]
    (let [tasks (:tasks db)]
      (if (nil? tasks)
        true
        (->> tasks
             (every?
              (fn [task]
                (some #(= (:category-id task))
                      (:categories db)))))))))

(-> (gen/generate (s/gen ::un-linked-db))
    ()
 )

;; (s/def ::db (s/with-gen
;;               (s/and ::un-linked-db
;;                      ::task-category-links
;;                      ;; generate an un-linked-db and randomly assign category id's to tasks
;;                      #()
;;                      ))

(def default-db (gen/generate (s/gen ::un-linked-db)))


## License
???

## work space
- period 
  - assigns `onTouchStart` and `onMouseDown` event handlers to itself **if** selection is not period
    - they select the period 
  - assigns `onTouchStart` and `onMouseDown` event handlers to itself **if** selection is self
    - they set is moving period
- day 
  - assigns `onTouchEnd` `onMouseUp` **if** is-moving-period
    - they set is-moving-period false
  - assigns `onTouchMove` and `onMouseMove` **if** is-moving-period
    - they call `(handleMove)` fn
  - assigns `onTouchStart` and `onMouseDown` **if** selection is period 
    - sets selection nil

- when a period is selected (but not moving) there will be two indicators on each end for stretching and shrinking and an indicator in the center for sliding
- movement will alter the _middle time_ instead of the start time
  - this can be done by taking the _time_ of the position and setting start 
 ```
  pos_time = start + (d/2)
  start = pos_time - (d/2) 
 ```
 
 - for dealing with tasks _straddling_ the division between yesterday and today (default 00:00) the rule shoudl be that
   - no task can be moved so that its stop time goes past end of day (default 23:59) 
   - In order to have a task go over day divisor it must be _selected_ and then _split_ through the action button.
   - _splitting_ will create two tasks of half the duration of the original. One will have a stop that ends on the current day divisor and the other will have a start that begins at the next day divisor


- [x] fix touchy touch handler
- [x] add other stretch handle (rename circle to drag handle)
- [x] place/implement zoom in buttons
- [ ] change drag to move by mid-point
- [ ] implement stretch handle
