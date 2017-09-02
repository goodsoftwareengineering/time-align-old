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
      - [x] queue (any without start/stop)
    - [x] update
      - [x] slidding
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

## License
???

## work space
- [x] finish working out stubs for all action button set state
- [x] effects for selecting periods change appropriate action button state

- [ ] create forms
  - [x] leaving id blank generates new in handler
  - [x] category
    - [x] color selector
    - [x] save new category form
    - [x] remove tabs 
    - [x] clean up save form action (navigates away)
    - [x] work out how to get to edit version of form
    - [x] cancel button
  - [x] task
  - [ ] period
    - [ ] move time picker state to app-db
    - [ ] task picker (do the quick thing and just display all the task sorted by category and then name)

- [ ] edit forms
  - [ ] category
    - [ ] delete button (change cancel to disabled color and delete to secondary)
  - [ ] tasks
    - [ ] delete button
  - [ ] periods
    - [ ] delete button

- [ ] list (nested list component Categories->tasks->periods)
- [ ] list item selection goes to edit form
- [ ] action button edit goes to edit form
- [ ] account page
- [ ] settings page
- [ ] set top of the wheel time
- [ ] drawer links

- [ ] for periods straddling date divide use old stop value instead of new one in handlers
  - [ ] put the date in the viewers time zone to get the cut off right!!!

- [ ] logging
  - [ ] secretary url params for referer logs in db as referrer
  - [ ] set up a bare bones luminus server that logs to a sql lite db
    - [ ] needs to decrypt with hard coded key
    - [ ] authenticates with hard coded token
  - [ ] send info
    - [ ] initial db
    - [ ] every action
    - [ ] encrypt every send
    - [ ] add token
    
- [ ] automatic demo
  - [ ] set up the app to run a series of actions at specific ~~time intervals~~ to demo the app
  - [ ] configure a snack bar with `next` and `close` buttons

- [ ] market
  - [ ] post to reddits (personalize each link with referrer `https://timealign.github.io/#/clojure`)
    - [ ] gather stats on audience total size for each subredit
    - [ ] clojure
    - [ ] cljs
    - [ ] data is beautiful
    - [ ] time management

- [ ] analytics processing 
  - [ ] answer questions
    - [ ] how many unique ip visits from each referrer?
    - [ ] how many sessions for each unique ip?
    - [ ] time spent in app
  - [ ] construct a sort of tree of each session action series overlaid (example below)

```
_ arrived __ clicked floating action _____ ??? 
          \____ clicked period ______ ??? _____
                                      \_____
```

## techincal challenges
- animations
- responsive design
  - https://github.com/Jarzka/stylefy
- routing and pretty urls

## long term cleanup
- pull out all state from core and put it in view
- merge selectio handlers into an entity selection handler
- all handlers have non-anon functions
- all subscriptions have non-anon functions
- custom svg defn's have name format
  - svg-(mui-)-[icon-name]
- enforced rule for subs/handlers
  - only give back individual values or lists never chunks of structure?
- all subscriptions in render code at top most levels and fed down --- Maybe not...
  - will make testing easier
  - seems too _complex_ to have individual components subscribe to things
  - then would too many components be unessentially injecting subs they dont' care about for their children?
