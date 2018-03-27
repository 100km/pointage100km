#Configuration

##infos.json

This file is a global file common to all sites. For the following we consider N to be your number of sites where the programm will run. Here are the different keys to configure for your race :

 * "sites" : an array of N string containing the name of yoir sites
 * "kms_offset": an array of N reals containing the distance from the start to each site.
 * "kms_lap": the distance of one lap
 * "races_names": an array with the name of the different races tobe displayed in graphical interface
 * "start_times": an array with the start time of each race (in milli seconds since epoch)
 * "cat_names": an array with the name of the category


##server configuration

On the main server, you need:
 * couchdb running
 * a database steenwerck-config containing:
   * one document with id "configuration", with a field "dbname", containing the database name used by all other programs and a field "test_allowed"
   containing the boolean `false` which will be overridden in testable databases;
   * a security configuration allowing the main user to read this database.
 * a database with the name indicated in "steenwerck-config" database containing the couchapp

##client configuration

On each client, you need:
 * couchdb running
 * nautilus shouldn't open a window when inserting USB drive : `gsettings set org.gnome.desktop.media-handling automount-open false`

##steenwerck.conf

This file is not commited on git. It is a file containing the information to log on the server couchdb.

```
master {
  host = <server hosting the couchdb>
  port = <port to reach couchdb>
  user = <user used for server connection>
  password = <password used for server connection>
}
```

and many more things.

Similarly, a `local` section can be added in this file to override local database information. Parameters for the `replicate`
application can be overridden in a `replicate` section.

## Preparing a new race

**This may be a duplicate of the next section but is hopefully more up-to-date.**

Ensure that the main server is running:

```bash
server$ /kvm/launch-steenwerck
```

Ensure that you have a `steenwerck.conf` file containing at least the `steenwerck` and `loader` sections.

On a developer PC, ensure that `infos.json` contains the right starting date for the race:

```bash
dev$ cd couchapps
dev$ ./set-start-times.sh DAY
dev$ ./set-start-times.sh  # To control
dev$ git commit -m "Set start times" _docs/infos.json
dev$ git push
dev$ cd ..
```

On a developer PC, rotate the database and load the scripts:

```bash
dev$ bin/wipe login password
dev$ couchapps/server-pushapps server login password
```

On the server, restart the `steenwerck-replicate` docker:

```bash
server$ docker kill steenwerck-replicate
server$ /kvm/launch-steenwerck
server$ docker ps
```

You should see `steenwerck-couchdb` and `steenwerck-replicate` containers running.

On a developer PC, start a mysql tunnel towards the main database:

```bash
dev$ ssh -L 3306:localhost:3306 100kmsteenwerck.fr
```

In another terminal, start the loader script:

```bash
dev$ bin/loader -r 1 YEAR
```

This will block and reload the contestants list every minute.

Then restart the docker container on the web site.

## Launching a race

If a bib has been input in the bib_input couchapp before the contestant had been transfered from the website db to couchdb, (for example you forgot to run bin/loader, or a contestant registered after the begining of the race), you need to have a bin/replicate launched in master mode to fix the checkpoint document

STEPS:
-1. On the db server:
```
$ docker rm -f steenwerck-replicate
```
0. @race HQ (with internet); estimated time 1h
  - NEEDS: pen and post-its, 1 developper PC, 7 checkpoints PCs, 1 display PC with video projector.
  - On the developper pc:
    - make sure you have the latest sources
    - do not forget to checkout submodules: git submodule update --init
    - compile scala: make
    - Optional: Do a rapid test of the app:
      - On the developper PC:
        - launch ./wipe
        - set the start times of the races to now + 10 minutes: in couchapps/_docs/infos.json, put the value of `echo $(($(date +%s)*1000 + 10*60*1000))`
        - push couchapps: cd couchapps && ./server-pushapps server LOGIN PASSWD
        - ensure couchdb is running on developper PC.
        - launch replicate mirror
        - establish tunnel with mysql server : ssh -L 3306:localhost:3306 SERVERNAME (maybe need to stop local mysql to release port 3306)
        - launch loader with 100km_prod credentials (lookup website code): `bin/loader -u 100km_prod -p PASSWD -d 100km_prod 2014`
        - insert a few bibs
        - set the start times of the races to show the morning race
        - insert a few bibs
      - On the display PC
        - launch sudo rm -rf /var/lib/puppet
        - launch sudo puppet agent --test
        - launch replicate slave
        - see that bibs appear in http://localhost:5984/steenwerck100km/_design/bib_input/pointage.html
        - see that rankings appear without morning race @ http://localhost:5984/steenwerck100km/_design/main_display/classement.html
        - see that rankings appear with morning race @ http://localhost:5984/steenwerck100km/_design/main_display/classement.html
        - polish pixel accurate display :-)
    - Really start the race:
      - launch ./wipe
      - !!Check that the times of the start of the races are correct
        - `echo $(($(date +%s --date="28 May 2014 19:00:00")*1000))` !!
        - `echo $(($(date +%s --date="29 May 2014 06:00:00")*1000))` !!
      - check that everything you are couchapp_pushing is committed and pushed to git (infos.json, JS, HTML, ...)
      - push couchapps: cd couchapps && ./server_pushapps server LOGIN PASSWD
      - ensure couchdb and replicate are running on developper PC.
        - `docker run --name steenwerck-replicate -p 5984:5984 -v ~/steenwerck.conf:/steenwerck.conf rfc1149/pointage100km replicate mirror`
      - establish tunnel with mysql server : ssh -L 3306:localhost:3306 SERVERNAME (maybe need to stop local mysql to release port 3306)
      - launch loader with 100km_prod credentials (lookup website code): `bin/loader -u 100km_prod -p XXXXXX -d 100km_prod [-r minutes] YEAR`
      - launch `./couchsync --init` to prepare one or more USB keys with the right password, and follow the instructions
      - launch replicate master on the main server : `/kvm/launch-steenwerck`
      - setup correct log level in the documents officer-<name> in the server database
  - foreach checkpoint pc X:
    - launch sudo rm -rf /var/lib/puppet
    - launch `docker volume ls -q | grep -x steenwerck-data | xargs -r docker volume rm` to clear previous race data
    - launch `sudo puppet agent --test --waitforcert 3 --server <SERVER_NAME>`
    - launch `sudo firewall_100km_start`
    - open screen
    - in screen, launch
        - `docker ps -a -q -f name=steenwerck-replicate | xargs -r docker rm -f` to ensure no previous containers
        - `docker run --rm --add-host steenwerck.rfc1149.net:10.8.0.1 --name steenwerck-replicate -p 5984:5984 -v ~/steenwerck.conf:/steenwerck.conf -v steenwerck-data:/opt/couchdb/data rfc1149/pointage100km replicate checkpoint X`
    - in screen, launch `./couchsync --watch`
    - in screen, launch the appropriate command for getting 3G Internet:
       - !! if X == 6 || X == 3, no need for USB key, use the wifi network
       - use `clef[1-7]` scripts (should be in your PATH in /usr/local/bin)
    - detach screen
    - stick the post-it with X and the name on the pc
    - for checkpoint0, setup 2 PCs and share the connection on the first PC
    - put to sleep.
  - On the 100km website:
    - launch `docker pull rfc1149/pointage100km`
    - launch `docker run --name steenwerck-replicate -p 5984:5984 -v ~/steenwerck.conf:/steenwerck.conf rfc1149/pointage100km replicate slave`
    - launch friends app
      - go to /usr/local/var/lib/node/100km-results
      - run grunt browserify (to build the webapp)
      - mv the old 100km.sqlite to 100km-<year>.sqlite
      - run grunt create-database (to recreate 100km.sqlite)
      - systemctl restart node-100kmresults-server

1. @all checkpoints site
  - NEEDS: 7 checkpoints PCs, 7 3G keys, 14 pens, paper, site keys;
  - wakeup pc, setup 3G, put 2pen and paper
  - open pointage couchapp @ localhost:5984/steenwerck100km/_design/bib_input/pointage.html
  - set fullscreen (F11)
  - only at the finish line: setup the display pc :
    - setup LAN to finish line checkpoint PC
    - ensure couchdb is not running on the display pc:
      - sudo /usr/local/etc/init.d/couchdb stop
      - check "ps aux | grep beam" and "ps aux | grep couchdb" returns nothing but the grep process
      - if necessary "sudo killall -u couchdb", recheck ps aux
      - in a screen, setup ssh tunel to checkpoint pc: ssh -L 5984:127.0.0.1:5984 steenwerck@192.168.42.1
    - open "main_display" couchapp on localhost: http://localhost:5984/steenwerck100km/_design/main_display/classement.html
    - setup double display and close the lid

to keep in mind:
- When you launch wipe, ALL replicate processes running on checkpoints pcs MUST be stopped prior entering new checkpoints,
  All data entered before the wipe that has not been synchronized to the server can be considered lost (if you really need to get them, they will actually be deleted only when replicate is restarted)
- When you push a couchapp to be used in the race, you must push all your changes to github, else they will be overwritten (even info.json)
- main_display loads all the contestants names at the opening of the page, so you MUST reload main_display every time you load new contestants using loader (if you do not, the new constestants will still show in main_display, but their names will be blank)

##After the race
- make sure to fix all problems in http://localhost:5984/steenwerck100km/_design/admin/admin.html
- go to http://localhost:5984/steenwerck100km/_design/search/_list/times-to-csv/all-times-and-infos?include_docs=true to have the detailed results and put ino MySQL. DOn't forget to change the last `,` into a `;`

##Troubleshooting:
   - If you get
```
[Replicator-akka.actor.default-dispatcher-3] ERROR Replicate(akka://Replicator) - deletion failed: java.net.ConnectException: Connection refused
[Replicator-akka.actor.default-dispatcher-3] ERROR Replicate(akka://Replicator) - cannot create database: java.net.ConnectException: Connection refused
```
Did you forget to launch couchdb on the local computer ? Is it in admin party mode (no explicit administrator account defined)?

##Notes

For now, all the tool expects to be in admin party mode. Maybe we should change that in the future…
