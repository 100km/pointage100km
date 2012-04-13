#CouchDB installation

As stated in DATABASE.mb, please install couchDB version 1.2.0

Here are the commands used to install couchDB on Ubuntu 11.10:
<pre>
sudo apt-get install libmozjs185-dev libicu-dev libcurl4-gnutls-dev erlang 
wget http://wwwftp.ciril.fr/pub/apache/couchdb/releases/1.2.0/apache-couchdb-1.2.0.tar.gz
tar -xzvf apache-couchdb-1.2.0.tar.gz
cd apache-couchdb-1.2.0
./configure
make
sudo make install
</pre>


Here are the commands used to install couchDB on Debian 6.0.3:
<pre>
$ sudo apt-get install libmozjs-dev libicu-dev libcurl4-gnutls-dev
$ sudo apt-get install erlang
$ wget http://wwwftp.ciril.fr/pub/apache/couchdb/releases/1.2.0/apache-couchdb-1.2.0.tar.gz
... (same as ubuntu instructions)...
</pre>

then as stated here:
   http://git-wip-us.apache.org/repos/asf/couchdb/repo?p=couchdb.git;a=blob;f=INSTALL.Unix;hb=master
do the following:

<pre>
$ sudo adduser --system --home /usr/local/var/lib/couchdb --no-create-home --shell /bin/bash --group --gecos "CouchDB Administrator" couchdb
$ sudo chown -R couchdb:couchdb /usr/local/etc/couchdb
$ sudo chown -R couchdb:couchdb /usr/local/var/run/couchdb
$ sudo chown -R couchdb:couchdb /usr/local/var/log/couchdb
$ sudo chown -R couchdb:couchdb /usr/local/var/lib/couchdb
$ sudo chmod 0770 /usr/local/etc/couchdb
$ sudo chmod 0770 /usr/local/var/lib/couchdb
$ sudo chmod 0770 /usr/local/var/log/couchdb
$ sudo chmod 0770 /usr/local/var/run/couchdb
$ sudo /usr/local/etc/init.d/couchdb status
$ sudo /usr/local/etc/init.d/couchdb start
</pre>


Here are the commands used to install couchDB on Ubuntu 10.04:
<pre>
sudo apt-get install g++ libssl-dev libcurses-dev libicu-dev libcurl4-gnutls-dev
# Install Spider Monkey (javascript engine)
wget http://ftp.mozilla.org/pub/mozilla.org/js/js185-1.0.0.tar.gz
tar -xzvf js185-1.0.0.tar.gz
cd js-1.8.5/js/src
./configure
make
sudo make install
# Install Erlang
wget http://www.erlang.org/download/otp_src_R15B.tar.gz
tar -xzvf otp_src_R15B.tar.gz
cd otp_src_R15B/
./configure
make
sudo make install
# Then follow instructions for Ubuntu 11.10:
sudo apt-get install libmozjs185-dev libicu-dev libcurl4-gnutls-dev erlang
wget http://wwwftp.ciril.fr/pub/apache/couchdb/releases/1.2.0/apache-couchdb-1.2.0.tar.gz
tar -xzvf apache-couchdb-1.2.0.tar.gz
cd apache-couchdb-1.2.0
./configure
make
sudo make install
</pre>

Here are the commands used to install couchDB on Mac OS X
<pre>
sudo port install couchdb
sudo mkdir /opt/local/var/run/couchdb
sudo chown -R couchdb:couchdb /opt/local/var/lib/couchdb/ /opt/local/var/log/couchdb/ /opt/local/etc/couchdb/ /opt/local/var/run/couchdb/
# Then to start couchDB
sudo -u couchdb couchdb
</pre>

#couchapp installation

For now, there is no recommended version of couchapp. Versions 0.7 and 1.0.1 have been tested.

Here are the commands to install couchapp version 1.0.1 on Ubuntu 11.10:
<pre>
sudo apt-get install python-setuptools
sudo easy_install pip
sudo pip install git+http://github.com/couchapp/couchapp.git@1.0.1#egg=Couchapp
// When asked for bitbucket username and password just hit enter
</pre>

Here are the commands to install couchapp version 1.0.1 on Mac OS X
<pre>
sudo port install python26
sudo port install py26-pip
# Check the version of python installed on your computer by running
python -V
which python
# if needed delete /usr/local/bin/python to leave only /opt/local/bin/python
# check the version of pip (the executable in /opt/local/bin is suffixed by the version number)
sudo pip-26 install git+http://github.com/couchapp/couchapp.git@1.0.1#egg=Couchapp
# Create a symbolic link
sudo ln -s /opt/local/Library/Frameworks/Python.framework/Versions/2.6/bin/couchapp /opt/local/bin/couchapp
</pre>

#try the couchapp

In order to try the couchapp, please configure couchdb to have local administrative user to be "admin" with password "admin".  

#####Method 1:
Stop couchdb  
Edit the `local.ini` file of couchdb
<pre>
[admins]
admin=admin
</pre>
Restart couchdb: `sudo -u couchdb couchdb` (for those who run it on a command line)

#####Method 2:
Go to http://localhost:5984/_utils/ and look at the **bottom right corner** to create a admin account 

Then, just go to `couchapps` directory and run `./pushapps.bash`. The different couchapps are available at http://localhost:5984/steenwerck100km/_design/`couchapp_name`/`page_name`.html

For example, bib_input is located at http://localhost:5984/steenwerck100km/_design/bib_input/pointage.html

You can visit http://localhost:5984/_utils/ and see that it created a database `steenwerck100km`

#Initialise DB

Initialize the site_id with these commands:
<pre>
cd pointage100km
./sbt
</pre>

wait a bit and enter the following on the prompt  
note: {0,1,2} is the site_id you must choose one among those values  
note2: this needs a .cfg file that is not in the repository
<pre>
project replicate
run {0,1,2}
</pre>

then, to initialise the participant's names with these commands:

<pre>
./sbt
</pre>

wait until the prompt, then

<pre>
project loader
run initialize_db/100km2012.mdb
</pre>


IS THIS STILL REQUIRED NOW THAT WE USE LOADER?  
Note: you might need to install mdbtools to run mdb2couchdb.bash
<pre>
sudo apt-get install mdbtools
</pre>
