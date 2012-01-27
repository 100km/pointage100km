JARFILES = bin/replicate.jar bin/couchsync.jar bin/wipe.jar

all:: $(JARFILES)

clean::
	./sbt root/clean

distclean::
	$(MAKE) clean
	$(RM) $(JARFILES)

%.jar: ALWAYS
	./sbt `basename ${@:.jar=}`/proguard

ALWAYS::
