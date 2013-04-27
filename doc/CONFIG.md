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
        - set the start times of the races to now.
        - push couchapps: cd couchapps && ./server_pushapps server LOGIN PASSWD
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
      - launch replicate with site_id 0
      - establish tunnel with mysql server : ssh -L 3306:localhost:3306 SERVERNAME (maybe need to stop local mysql to release port 3106)
      - launch loader with 100km_prod credentials (lookup website code)
      - (FIXME do we want to to this)update puppet master to distribute etc/network/interfaces with auto ppp0
  - foreach checkpoint pc X:
    - launch sudo puppet agent --test
    - launch ./replicate X; Stick the post-it with X and the name on the pc;
      - !! if X == 6, launch replicate --full !!
    - put to sleep.

1. @all checkpoints site
  - NEEDS: 7 checkpoints PCs, 7 3G keys, 14 pens, paper, site keys;
  - wakeup pc, setup 3G, put 2pen and paper
  - open pointage couchapp @ localhost:steenwerck100km/_design/bib_input/pointage.html
  - set fullscreen (F11)
  - only at the finish line: setup the display pc :
    - setup LAN to finish line checkpoint PC
    - open "main_display" couchapp on the database of the checkpoint pc for the site_id 6

to keep in mind:
- When you launch wipe, ALL replicate processes running on checkpoints pcs MUST be stopped prior entering new checkpoints,
  All data entered before the wipe that has not been synchronized to the server can be considered lost (if you really need to get them, they will actually be deleted only when replicate is restarted)
- When you push a couchapp to be used in the race, you must push all your changes to github, else they will be overwritten (even info.json)

##Notes

For now, all the tool expects to have a local "admin/admin" account. Maybe we should change that in the future...
