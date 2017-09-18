# time align
Life is how you spend time. This tool will help you work towards aligning what you plan and what you do.  

generated using Luminus version "2.9.11.46"

## prerequisites to dev
- [Vagrant](https://www.vagrantup.com/)
- [VirtualBox](https://www.virtualbox.org/wiki/VirtualBox)
- [nRepl enabled editor](https://cb.codes/what-editor-ide-to-use-for-clojure/)

## running dev
- clone repo
- run `vagrant up` (in a command line environment)
- run `bash start.sh` or `start.bat` (will open an ssh connection to vm)
- run `./cider-deps-repl` (in vm, will start an nrepl)
- connect to nrepl at port 7000 (using nrepl enabled editor)
- run in nrepl `(start)` (launches the server)
- run in nrepl `(start-fw)` (transpiles cljs and starts figwheel server)
- run in nrepl `(cljs)` (starts a clojurescript repl in your browser that will connect automagically when you open localhost:3000)

## launches
### alpha
- web app 
- no syncing
- no auth
### beta
- web app
- api
- with syncing 
- oauth via google
### 1.0
ios + android + webapp

## sequence of events
- finish [working client](#working-client)
- [refactor](#first-great-refactor) skeleton to be readable and maintainable without changing any functionality
- consider [ux progress](#ux-progress) and what is _practical_ to do at this point
- add [extras needed to launch proof of concept](#extras-needed-to-launch-proof-of-concept)
- launch alpha SPA on github
- simultaneously
  - data base configuration
  - resty api
  - rest of _possible_ [ux progress](#ux-progress)
- launch paid for beta (full app front & back)
- figure out a react native framework
- launch 1.0

## goals
### Mark
- 20170923
  - app-db ported to cljc
  - utils ported to cljc
  - tests for utils
### Justin
- 20171001 
  - finish [working client](#working-client)
### Global
- 20171115
  - finish [great refactor](#first-great-refactor)
- 20171231
  - proof of concept launch

## working client
- [x] finish working out stubs for all action button set state
- [x] effects for selecting periods change appropriate action button state

- [x] create forms
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

- [x] edit forms
  - [x] alter page handler
    - [x] fx
    - [x] if id is not nil dispatch a load entity to form
  - [x] category
    - [x] navigate to edit particular
    - [x] submit handler works to update
    - [x] delete button (change cancel to disabled color and delete to secondary)
  - [ ] tasks
    - [x] navigate to edit particular
    - [x] submit handler works to update
    - [x] delete button
  - [x] periods
    - [x] delete button
    - [x] dispatch fx change page
    - [x] dispatch fx set selected nil
    - [x] planned toggle
    
- [x] list (nested list component Categories->tasks->periods)
  - [x] figure out nested list ui component
  - [x] remove entities from drawer replace with list option
  - [x] render nested list with appropriate icons
  - [x] clicking list item navigates to edit page
- [x] account page
  - [x] name
  - [x] email
  
- [x] category doesn't clear on save form
- [x] opening list after creating category errors

- [ ] periods straddling date divide 
  - [x] determine if starts yesterday
  - [x] render yesterday indicator
  - [ ] determine if ends tomorrow
  - [ ] render tomorrow indicator
  - [ ] put the date in the viewers time zone to get the cut off right ???
     - does this mean only in the utility function for determining cutt off?
  
- [ ] for straddling now
  - [ ] split into two arcs so that opacity changes make sense
  
- [ ] settings page
  - [ ] set top of the wheel time
    - this does not change the date divide just rotates the wheel display
    - date divide is still 00:00
    - consider letting date divide be a setting
      - if the date divide was 06:00 then the rendered day would be 06:00 (today) - 05:59 (tomorrow)
      - this might be better in the next release
  - [ ] keep a date divide indicator will be at 00:00 or 0 deg
    - deg shouldn't change for rendering because the whole visual will likely be rotated
      - unless that isn't performant
      - then deg need to be adjusted and relative
- [ ] drawer links
 
- [ ] action buttons

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

## first great refactor
### tests
- utils functions
- every handler
- maybe test view only helper functions

### other
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
 
## ux progress
### thoughts
- idiomatic organization
  - user case CGP grey
    - optiona A
      - category : videos
      - task     : video x (task completes when video x is done)
      - period   : edit script (needs some sort of searchable identifer meta data to say it is scripting and not editing)
    - option B
      - category : videos
      - task     : scripting videos (task never completes)
      - period   : video x
- copy option on all entity types (put in edit form and on press navigate to another edit form of new entity with something blank so it can't be submitted)
- some sort of template system (sets of category > task > periods or task > periods or just periods) that can be generated by a shortcut
- [push notifications](https://developers.google.com/web/fundamentals/engage-and-retain/push-notifications/)
  - when a playing period is planned to end
  - before a planned period starts
  - ever {user set interval} after a playing periods planned counterpart has ended
- queue needs two options (tabs)
  - queue of tasks with no stamps
  - queue of upcoming planned tasks
### actionable


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

## license
Copyright 2017 Justin Good

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
