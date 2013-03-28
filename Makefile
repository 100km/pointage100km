JARFILES = bin/replicate.jar bin/couchsync.jar bin/wipe.jar bin/loader.jar bin/stats.jar bin/loaderaccess.jar
BINFILES = $(JARFILES:.jar=)
DIST = bin.tar.xz
ROOTDIR = server
SBT = ./sbt

all:: assembly

assembly:: $(JARFILES)

dist:: $(DIST)

$(DIST): $(JARFILES) $(BINFILES)
	$(RM) $(DIST)
	tar Jcvf $(DIST) $(JARFILES) $(BINFILES)

clean::
	cd $(ROOTDIR) && $(SBT) root/clean

distclean::
	$(MAKE) clean
	$(RM) $(JARFILES)

check::
	cd $(ROOTDIR) && $(SBT) root/test

%.jar: ALWAYS
	cd $(ROOTDIR) && $(SBT) `basename ${@:.jar=}`/assembly

ALWAYS::
