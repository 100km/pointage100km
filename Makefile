JARFILES = bin/replicate.jar bin/wipe.jar bin/loader.jar bin/stats.jar bin/loaderaccess.jar
BINFILES = $(JARFILES:.jar=)
DIST = bin.tar.xz
ROOTDIR = server
SBT = ./sbt
GITVER := $(shell git describe --long --always)

all:: assembly

incremental::
	if [ x$(GITVER) != x$$(cat .gitrev 2> /dev/null) ]; then $(MAKE) assembly; fi

.gitrev: ALWAYS
	echo $(GITVER) > .gitrev

assembly:: $(JARFILES)
	$(MAKE) .gitrev

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
