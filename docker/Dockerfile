FROM rfc1149/couchdb:1.6.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net

ADD webupd8team-java.list /etc/apt/sources.list.d/
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections

ADD cors.ini /usr/local/etc/couchdb/local.d/

RUN apt-get update && \
    apt-get install -y --force-yes --no-install-recommends oracle-java8-installer oracle-java8-set-default git make && \
    rm -rf /var/cache/oracle-jdk-installer

RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck

USER steenwerck
RUN cd /home/steenwerck && \
    git clone --recursive https://github.com/100km/pointage100km.git && \
    cd pointage100km && \
    make bin/replicate && \
    find server -name target -print0 | xargs -0 rm -rf && \
    cd .. && \
    rm -rf .sbt .ivy2

USER root
ADD start.sh /
RUN chmod 755 /start.sh
ENTRYPOINT [ "/start.sh" ]

# It is likely that you will want to extend this Dockerfile by adding a configuration
# file with -v source:target when creating the container. Also, you might need to
# create CouchDB administrators by extending this Dockerfile and adding new *.ini files
# into /usr/local/etc/couchdb/local.d/.
