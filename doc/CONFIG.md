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

##Notes

For now, all the tool expects to have a local "admin/admin" account. Maybe we should change that in the future...
