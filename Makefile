JARFILES = bin/replicate.jar bin/couchsync.jar bin/wipe.jar bin/loader.jar
BINFILES = $(JARFILES:.jar=)
DIST = bin.tar.xz
SBT = ./sbt

all:: proguard

proguard:: $(JARFILES)

dist:: $(DIST)

$(DIST): $(JARFILES) $(BINFILES)
	$(RM) $(DIST)
	tar Jcvf $(DIST) $(JARFILES) $(BINFILES)

clean::
	$(SBT) root/clean

distclean::
	$(MAKE) clean
	$(RM) $(JARFILES)

check::
	$(SBT) root/test

%.jar: ALWAYS
	$(SBT) `basename ${@:.jar=}`/proguard

ALWAYS::
