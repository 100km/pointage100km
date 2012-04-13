#!/bin/sh
if test -z "$SBT_OPTS"; then
  SBT_OPTS="-Xms512m -Xmx1024m -XX:ReservedCodeCacheSize=64m -XX:MaxPermSize=256m -Xss2m -XX:+CMSClassUnloadingEnabled -Dfile.encoding=UTF-8"
fi
java $SBT_OPTS -jar project/sbt-launch.jar "$@"
