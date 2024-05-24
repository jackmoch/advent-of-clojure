FROM babashka/babashka:1.3.190-alpine

RUN apk update && apk add bash
RUN curl -sLO https://raw.githubusercontent.com/clj-kondo/clj-kondo/master/script/install-clj-kondo &&\
    chmod +x install-clj-kondo &&\
    ./install-clj-kondo

# For local development only
#COPY ./scripts ./scripts
#COPY ./src ./src

COPY ./linting.clj /usr/local/bin/bb/

ENTRYPOINT ["/usr/local/bin/bb/linting.clj"]