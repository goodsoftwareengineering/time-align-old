FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/time-align.jar /time-align/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/time-align/app.jar"]
