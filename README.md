# time align

Life is how you spend time. Use this tool to make goals about spending time and track how you actually spend time. Work towards aligning those.  

## sequence of events
- finish working skeleton
- refactor skeleton to be readable and maintainable without changing any functionality
- add [extras needed to launch proof of concept](#extras-needed-to-launch-proof-of-concept)
- launch proof of concept SPA on github
- starting adding things
  - functionality / ux changes to spa
  - back end api and database structure
- launch paid for beta (full app front & back)
- ???
- profit

## goals
### Mark
- 20170923
  - app-db ported to cljc
  - utils ported to cljc
  - tests for utils
### Justin
- 20171001 
  - finish [working skeleton](#working-skelton)
### Global
- 20171115
  - finish [great refactor](#first-great-refactor)
- 20171231
  - proof of concept launch

## prerequisites to dev
- [Vagrant](https://www.vagrantup.com/)
- [VirtualBox](https://www.virtualbox.org/wiki/VirtualBox)
- [nRepl enabled editor](https://cb.codes/what-editor-ide-to-use-for-clojure/)



## running
- clone repo
- run `vagrant up`
- run `bash start.sh` or `start.bat` (will open an ssh connection to vm)
- run `./cider-deps-repl` (in vm, will start an nrepl)
- connect to nrepl at port 7000
- run in nrepl `(start)` (launches the server)
- run in nrepl `(start-fw)` (transpiles cljs and starts figwheel server)
- run in nrepl `(cljs)` (starts a clojurescript repl in your browser that will connect automagically when you open localhost:3000)


## working skeleton
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
  - [x] period
    - [x] move time picker state to app-db
    - [x] description
    - [x] task picker (do the quick thing and just display all the task sorted by category and then name)
    - [x] add some ad-hoc error display

- [ ] edit forms
  - [x] alter page handler
    - [x] fx
    - [x] if id is not nil dispatch a load entity to form
  - [ ] category
    - [ ] delete button (change cancel to disabled color and delete to secondary)
  - [ ] tasks
    - [ ] delete button
  - [x] periods
    - [x] delete button
    - [x] dispatch fx change page
    - [x] dispatch fx set selected nil
    - [ ] add planned toggle
    
- [ ] list (nested list component Categories->tasks->periods)
  - [ ] figure out nested list ui component
  - [ ] remove entities from drawer replace with list option
- [ ] list items have actionable button edits
- [ ] action button edit goes to edit form
- [ ] account page
  - [ ] name
  - [ ] email
- [ ] settings page
  - [ ] set top of the wheel time
- [ ] drawer links

- [ ] for periods straddling date divide 
  - [ ] use end of day const to render (maybe add indicator that period continues?)
  - [ ] use old stop value instead of new one in handlers ??
  - [ ] put the date in the viewers time zone to get the cut off right!!!
  

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




## tests
- [ ] utils functions
- [ ] every handler
- [ ] maybe test view only helper functions


## first great refactor
- use [specter](https://github.com/nathanmarz/specter) to get rid of ugly merge stuff in handlers
- merge `:planned-periods` and `:actual-periods` in app-db
  - add back a `:type` flag
  - refactor handlers, components to work with new structure
- use spec on app-db to validate every action
- pull out all state from core and put it in view
- merge selection handlers into an entity selection handler
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
 

## extras needed to launch proof of concept
- [ ] analytics
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
    - [ ] productivity

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


## bugs to fix later
- firefox moving periods
- 11:59 ticker really thin


## techincal challenges to address in beta
- animations
- responsive design
  - https://github.com/Jarzka/stylefy
- routing and pretty urls

 

## ux changes
- queue needs two options (tabs)
  - queue of tasks with no stamps
  - queue of upcoming planned tasks




## license
generated using Luminus version "2.9.11.46"
???
