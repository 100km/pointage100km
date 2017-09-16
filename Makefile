BINFILES = bin/replicate bin/wipe bin/loader bin/stats
DIST = bin.tar.xz
ROOTDIR = server
SBT = bin/sbt
GITVER := $(shell git describe --long --always)

all:: assembly

incremental::
	if [ x$(GITVER) != x$$(cat .gitrev 2> /dev/null) ]; then $(MAKE) assembly; fi

.gitrev: ALWAYS
	echo $(GITVER) > .gitrev

assembly:: $(BINFILES)
	$(MAKE) .gitrev

dist:: $(DIST)

$(DIST): $(BINFILES)
	$(RM) $(DIST)
	tar Jcvf $(DIST) $(BINFILES)

clean::
	cd $(ROOTDIR) && $(SBT) pointage100km/clean

distclean::
	$(MAKE) clean
	$(RM) $(JARFILES)

check::
	cd $(ROOTDIR) && $(SBT) pointage100km/test

$(BINFILES): ALWAYS
	cd $(ROOTDIR) && $(SBT) `basename ${@:.jar=}`/assembly

ALWAYS::
