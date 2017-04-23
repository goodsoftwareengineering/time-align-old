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

## License
???
