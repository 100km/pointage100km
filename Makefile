JARFILES = bin/replicate.jar bin/couchsync.jar bin/wipe.jar

all:: ${JARFILES}

clean::
	./sbt clean

%.jar: ALWAYS
	./sbt `basename ${@:.jar=}`/proguard

ALWAYS::
