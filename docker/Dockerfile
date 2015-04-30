FROM rfc1149/couchdb:1.6.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net

RUN apt-get update && \
    apt-get install -y --force-yes --no-install-recommends openjdk-7-jre-headless git make

RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck

USER steenwerck
RUN cd /home/steenwerck && \
    git clone --recursive http://github.com/100km/pointage100km.git && \
    cd pointage100km && \
    make bin/replicate && \
    find server -name target -print0 | xargs -0 rm -rf && \
    cd .. && \
    rm -rf .sbt .ivy2

USER root
ADD start.sh /
ENTRYPOINT [ "/start.sh" ]

# It is likely that you will want to extend this Dockerfile by adding a configuration
# file with -v source:target when creating the container. Also, you might need to
# create CouchDB administrators by extending this Dockerfile and adding new *.ini files
# into /usr/local/etc/couchdb/local.d/.
