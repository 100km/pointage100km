#CouchDB installation

As stated in DATABASE.mb, please install couchDB version 1.1.1

Here are the commands used to install couchDB on Ubuntu 11.10:
$ sudo apt-get install libmozjs185-dev libicu-dev libcurl4-gnutls-dev 
$ wget http://www.apache.org/dyn/closer.cgi?path=/couchdb/1.1.1/apache-couchdb-1.1.1.tar.gz
$ tar -xzvf apache-couchdb-1.1.1.tar.gz
$ cd apache-couchdb-1.1.1
$ ./configure
$ make
$ sudo make install

#couchapp installation

For now, there is no recommended version of couchapp. Versions 0.7 and 1.0.1 have been tested.

Here are the commands to install couchapp version 1.0.1 on Ubuntu 11.10:
$ sudo apt-get install python-setuptools
$ sudo easy_install pip
$ sudo pip install git+http://github.com/couchapp/couchapp.git@1.0.1#egg=Couchapp
// When asked for bitbucket username and password just hit enter

#try the couchapp

In order to try the couchapp, please launch couchDB and configure it to have local administrative user to be "admin" with password "admin".

Then, just go to bib_input directory and run "couchapp push". You should see a message like this:
// date info // [INFO] Visit your CouchApp here:
http://localhost:5984/steenwerck100km/_design/bib_input/index.html
