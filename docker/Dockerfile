FROM apache/couchdb:2.3.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net
RUN mkdir -p /usr/share/man/man1
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-8-jdk-headless git make
RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck
WORKDIR /tmp
RUN git clone https://github.com/100km/pointage100km.git
WORKDIR /tmp/pointage100km
RUN make bin/replicate

FROM apache/couchdb:2.3.0
MAINTAINER Samuel Tardieu, sam@rfc1149.net
RUN mkdir -p /usr/share/man/man1
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-8-jre-headless && \
    rm -rf /var/lib/{apt,dpkg}
COPY --from=0 /tmp/pointage100km/bin/replicate /usr/local/bin/
ADD cors.ini /opt/couchdb/etc/local.d/
ADD start.sh /
RUN chmod 755 /start.sh
ENTRYPOINT [ "/start.sh" ]
RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck
