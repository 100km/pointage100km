JARFILES = bin/replicate.jar bin/couchsync.jar bin/wipe.jar bin/loader.jar
SBT = ./sbt

all:: proguard

proguard:: $(JARFILES)

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
