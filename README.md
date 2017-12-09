# Time Align
Life is how you spend time. Use this to align what you _want_ with what you _do_.  

Generated using Luminus version "2.9.11.46"  

[Trello board](https://trello.com/b/kGu6Xm74/time-align)  

## Prerequisites to dev
- [Vagrant](https://www.vagrantup.com/)
  - version 2
- [Rsync](https://www.vagrantup.com/docs/synced-folders/rsync.html)
- [VirtualBox](https://www.virtualbox.org/wiki/VirtualBox)
  - version 5.1+
- [nRepl enabled editor](https://cb.codes/what-editor-ide-to-use-for-clojure/)

## Running dev
- clone repo
- run 
```
vagrant up
```
 (in a command line environment)
- run 
```
vagrant rsync-auto
```
 in the background or another terminal window
- run 
```
bash start.sh
```
 or 
```
start.bat
```
 (will open an ssh connection to vm)
- run 
```
lein run migrate
```
 (first time only)
- run 
```
./cider-deps-repl
```
 (in vm, will start an nrepl)
- connect to nrepl at port 7000 (using nrepl enabled editor)
- run in nrepl 
```
(start)
```
 (launches the server)
- run in nrepl 
```
(start-fw)
```
 (transpiles cljs and starts figwheel server)
- run in nrepl 
```
(cljs)
```
 (starts a clojurescript repl in your browser that will connect automagically when you open localhost:3000)
- run in nrepl 
```
(start-autobuild worker)
```
 to compile the web worker clojurescript files
 
## Running production
- compile jar
- set up database
- set up webserver
- run jar

## License
Copyright © 2017 Justin Good  

Distributed under the GNU General Public License v3 (GPL-3)


