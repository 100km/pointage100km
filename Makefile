JARFILES = bin/replicate.jar bin/couchsync.jar bin/wipe.jar

all:: ${JARFILES}

%.jar: ALWAYS
	./sbt `basename ${@:.jar=}`/proguard

ALWAYS::
