FROM rfc1149/couchdb:1.6.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net

RUN apt-get update
RUN apt-get install -y --no-install-recommends openjdk-7-jre-headless git make

RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck

USER steenwerck
RUN cd /home/steenwerck && git clone http://github.com/100km/pointage100km.git
RUN cd /home/steenwerck/pointage100km && git submodule update --init && make

USER root
ADD start.sh /
ENTRYPOINT [ "/start.sh" ]

# It is likely that you will want to extend this Dockerfile by adding a configuration
# file with -v source:target when creating the container. Also, you might need to
# create administrators by extending this Dockerfile and adding:
#   RUN echo "sam =  -hashed-beda[REDACTED]05fc" >> /usr/local/etc/couchdb/local.ini
# or if you don't care about cleartext passwords
#  RUN echo "admin = admin" >> /usr/local/etc/couchdb/local.ini
