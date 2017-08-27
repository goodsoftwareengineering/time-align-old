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
- [ ] lists
  - [ ] categories
  - [ ] tasks
  - [ ] periods
- [ ] edit forms
  - [ ] category
    - [ ] delete button (change cancel to disabled color and delete to secondary)
  - [ ] tasks
    - [ ] delete button
  - [ ] periods
    - [ ] delete button
- [ ] list item selection goes to edit form
- [ ] action button edit goes to edit form
- [ ] account page
- [ ] settings page
  - [ ] set top of the wheel time
- [ ] drawer links

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
