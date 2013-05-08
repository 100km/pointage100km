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
 * a database steenwerck-config containing with:
   * one document with id "configuration", with a field "dbname", containing the database name used by all other programs
   * a security configuration allowing the main user to read this database.
 * a database with the name indicated in "steenwerck-config" database containing the couchapp


##client configuration

On each client, you need:
 * couchdb running
 * "admin/admin" as main admin user


##steenwerck.cfg

This file is not commited on git. It is a file containing the information to log on the server couchdb.
[master]
host = <server hosting the couchdb>
port = <port to reach couchdb>
dbname = <LOCAL database name>
user = <user used for server connection>
password = <password used for server connection>

##Launching a race
If a bib has been input in the bib_input couchapp before the contestant had been transfered from the website db to couchdb, (for example you forgot to run bin/loader, or a contestant registered after the begining of the race), you need to have a bin/replicate launched with the -I option to fix the checkpoint document
TODO: describe the procedure to launch a race

STEPS:
0. @race HQ (with internet); estimated time 1h
  - NEEDS: pen and post-its, 1 developper PC, 7 checkpoints PCs, 1 display PC with video projector.
  - On the developper pc:
    - make sure you have the latest sources
    - compile scala: make
    - Optional: Do a rapid test of the app:
      - On the developper PC:
        - launch ./wipe
        - set the start times of the races to now + 10 minutes.
        - push couchapps: cd couchapps && ./server_pushapps server LOGIN PASSWD
        - ensure couchdb is running on developper PC.
        - launch replicate 0
        - establish tunnel with mysql server : ssh -L 3106:localhost:3106 SERVERNAME (maybe need to stop local mysql to release port 3106)
        - launch loader with 100km_prod credentials (lookup website code)
        - insert a few bibs
        - set the start times of the races to show the morning race
        - insert a few bibs
      - On the display PC
        - launch sudo puppet agent --test
        - launch replicate 0
        - see that bibs appear in localhost:steenwerck100km/_design/bib_input/pointage.html
        - see that rankings appear without morning race @ http://localhost:5984/steenwerck100km/_design/main_display/classement.html
        - see that rankings appear with morning race @ http://localhost:5984/steenwerck100km/_design/main_display/classement.html
        - polish pixel accurate display :-)
    - Really start the race:
      - launch ./wipe
      - !!Check that the times of the start of the races are correct!! check that everything you are pushing is committed and pushed to git
      - push couchapps: cd couchapps && ./server_pushapps server LOGIN PASSWD
      - ensure couchdb is running on developper PC.
      - launch replicate with site_id 0: ./bin/replicate 0
      - establish tunnel with mysql server : ssh -L 3306:localhost:3306 SERVERNAME (maybe need to stop local mysql to release port 3306)
      - launch loader with 100km_prod credentials (lookup website code): bin/loader -u 100km_prod -p XXXXXX -d 100km_prod YEAR
      - launch `./couchsync --init` to prepare one or more USB keys with the right password, and follow the instructions
  - foreach checkpoint pc X:
    - launch sudo puppet agent --test
    - open screen
    - in screen, launch ./replicate X; Stick the post-it with X and the name on the pc;
      - !! if X == 6, launch replicate --full !! 4
    - in screen, launch `sudo ./couchsync /usr/local/var/lib/couchdb`
    - in screen, launch the appropriate command for getting 3G Internet:
       - `pon SFR /dev/ttyUSB3` for SFR keys (white) 1, 2, 3, 5
       - `pon SFR /dev/ttyUSB0` for SFR key (white) 4
       - `pon BT /dev/ttyUSB2` for Bouygues Télécom keys (black)
    - detach screen
    - put to sleep.

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
      - in a screen, setup ssh tunel to checkpoint pc: ssh -L 5984:127.0.0.1:5984 steenwerck@steenwerck6
      - (if there is a problem with dns, lookup the ip address of steenwerck6, the checkpoint pc for the site 6)
    - open "main_display" couchapp on localhost: http://localhost:5984/steenwerck100km/_design/main_display/classement.html
    - setup double display and close the lid

to keep in mind:
- When you launch wipe, ALL replicate processes running on checkpoints pcs MUST be stopped prior entering new checkpoints,
  All data entered before the wipe that has not been synchronized to the server can be considered lost (if you really need to get them, they will actually be deleted only when replicate is restarted)
- When you push a couchapp to be used in the race, you must push all your changes to github, else they will be overwritten (even info.json)
- main_display loads all the contestants names at the opening of the page, so you MUST reload main_display every time you load new contestants using loader (if you do not, the new constestants will still show in main_display, but their names will be blank)

##Troubleshooting:
   - If you get
```
[Replicator-akka.actor.default-dispatcher-3] ERROR Replicate(akka://Replicator) - deletion failed: java.net.ConnectException: Connection refused
[Replicator-akka.actor.default-dispatcher-3] ERROR Replicate(akka://Replicator) - cannot create database: java.net.ConnectException: Connection refused
```
Did you forget to launch couchdb on the local computer ? Is it setup with admin/admin ?

##Notes

For now, all the tool expects to have a local "admin/admin" account. Maybe we should change that in the future...
