BINFILES = bin/replicate bin/wipe bin/loader bin/stats bin/loaderaccess
DIST = bin.tar.xz
ROOTDIR = server
SBT = ./sbt
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
	cd $(ROOTDIR) && $(SBT) root/clean

distclean::
	$(MAKE) clean
	$(RM) $(JARFILES)

check::
	cd $(ROOTDIR) && $(SBT) root/test

$(BINFILES): ALWAYS
	cd $(ROOTDIR) && $(SBT) `basename ${@:.jar=}`/assembly

ALWAYS::
