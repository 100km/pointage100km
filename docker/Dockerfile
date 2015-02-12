FROM klaemo/couchdb:1.6.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net

RUN apt-get update
RUN apt-get -y install openjdk-7-jre-headless git make

RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck

USER steenwerck
RUN cd /home/steenwerck && git clone http://github.com/100km/pointage100km.git
RUN cd /home/steenwerck/pointage100km && make

USER root
ADD start.sh /
ENTRYPOINT [ "/start.sh" ]

# It is likely that you will want to extend this Dockerfile with the following:
#  ADD steenwerck.cfg /home/steenwerck/pointage100km
#  RUN echo "sam =  -hashed-beda[REDACTED]05fc" >> /usr/local/etc/couchdb/local.ini
