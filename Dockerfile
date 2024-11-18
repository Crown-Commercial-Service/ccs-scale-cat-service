ARG APP_DIR=/app
FROM maven:latest as build
COPY . /build
RUN cd /build && mvn package

FROM openjdk:24-ea-22-oraclelinux8
ARG APP_DIR
RUN addgroup -S appuser && \
    adduser -S -G appuser appuser && \
    # Create application directory
    install -o appuser -g appuser -d ${APP_DIR}
RUN apk upgrade && apk add curl && rm -rf /var/cache/apk/*
COPY --chown=appuser:appuser --from=build /build/target/ccs-scale-cat-service-*.jar ${APP_DIR}/cat.jar
USER appuser
WORKDIR ${APP_DIR}
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "./cat.jar" ]
