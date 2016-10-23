FROM openjdk:8
RUN apt-get update
RUN apt-get -y install imagemagick ffmpeg libavcodec-extra
 
RUN mkdir /data
RUN chown nobody /data
WORKDIR /
COPY target/raoa-server-standalone*.jar /app.jar
USER nobody
EXPOSE 8080
ENV RAOA_ALBUM=/data
ENV RAOA_IMPORT=/import
VOLUME ["/data", "/import"]
CMD ["java","-jar","app.jar"]
