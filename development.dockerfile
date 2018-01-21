FROM clojure
RUN mkdir -p /usr/src/app/time-align
WORKDIR /usr/src/app/time-align
COPY project.clj /usr/src/app/time-align
RUN lein deps
COPY . /usr/src/app/time-align
