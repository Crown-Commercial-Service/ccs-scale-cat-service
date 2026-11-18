FROM amazoncorretto:27-alpine-jdk AS build

ARG MAVEN_VERSION=3.9.9
RUN apk add --no-cache curl tar fontconfig ttf-dejavu && \
    fc-cache -f && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar -xz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

WORKDIR /tmp/build
COPY . /tmp/build

RUN mvn clean package

# Use an official Corretto runtime as a base image
FROM amazoncorretto:27-alpine-jdk

# Set the working directory inside the container and install curl and needed fonts
WORKDIR /app
RUN apk add --update --no-cache \
    curl
RUN apk add --no-cache fontconfig ttf-dejavu

# Copy the application JAR file and external configuration files
COPY --from=build /tmp/build/target/ccs-scale-cat-service-*.jar /app/cat.jar

# Create the user and usergroup for the app to run as
RUN addgroup ujava && \
    adduser -S -G ujava ujava \
    && chown -R ujava: /app

USER ujava

# Set the working directory inside the container
WORKDIR /app

# Specify the profile to be used when running the application
ENV SPRING_PROFILES_ACTIVE=prod

# Set JVM options
ENV JAVA_OPTS="-Xmx2048m -Xms1024m"

# Expose the port your application will run on
EXPOSE 8080

# Command to run your application
ENTRYPOINT exec java $JAVA_OPTS -jar cat.jar
