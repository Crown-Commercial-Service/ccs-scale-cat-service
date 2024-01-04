ARG APP_DIR=/app
FROM maven:3.9.5-eclipse-temurin-17 as build
COPY . /build
RUN cd /build && mvn package

FROM eclipse-temurin:17.0.8.1_1-jre
ARG APP_DIR
RUN /usr/sbin/groupadd -r appuser && \
    /usr/sbin/useradd --no-log-init -r -g appuser appuser && \
    # Create application directory
    install -o appuser -g appuser -d ${APP_DIR}
COPY --chown=appuser:appuser --from=build /build/target/ccs-scale-cat-service-*.jar ${APP_DIR}/cat.jar
USER appuser
WORKDIR ${APP_DIR}
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "./cat.jar" ]
