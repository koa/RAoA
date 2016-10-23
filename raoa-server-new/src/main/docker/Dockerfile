# Build base image: registry.berg-turbenthal.ch:5000/raoa/base:latest

FROM openjdk:8
RUN apt-get update
RUN apt-get -y install imagemagick ffmpeg libavcodec-extra
 
RUN mkdir /data
RUN chown nobody /data