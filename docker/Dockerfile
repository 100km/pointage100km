FROM klaemo/couchdb:2.0.0
MAINTAINER Samuel Tardieu, sam@rfc1149.net

ADD webupd8team-java.list /etc/apt/sources.list.d/
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections

ADD cors.ini /opt/couchdb/etc/local.d/

RUN sed -ie s/https/http/ /etc/apt/sources.list.d/nodesource.list && \
    apt-get update && \
    apt-get install -y --force-yes --no-install-recommends oracle-java8-installer oracle-java8-set-default git make \
       build-essential libtool autoconf automake autoconf-archive pkg-config \
       libnspr4 libnspr4-0d libnspr4-dev libcurl4-openssl-dev libicu-dev python zip unzip && \
    rm -rf /var/cache/oracle-jdk-installer

# Override mozjs installation for grsec compatibility
RUN (cd /tmp && curl http://tomobox.fr/js185-1.0.0.tar.gz | tar zxf - && \
     cd js-1.8.5 && curl http://tomobox.fr/libmozjs185-grsec.patch | patch -p1 && \
     cd js/src && ./configure --prefix=/usr && make && make install) && \
    rm -rf /tmp/js-1.8.5

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
# into /opt/couchdb/etc/local.d/.
