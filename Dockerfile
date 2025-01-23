FROM maven:3.9.9-amazoncorretto-23 as build

RUN mkdir -p /tmp/build

WORKDIR /tmp/build

COPY . /tmp/build

RUN mvn clean install
RUN mvn package

# Use an official Corretto runtime as a base image
FROM amazoncorretto:23-alpine-jdk

# Set the working directory inside the container and install curl
WORKDIR /app
RUN apk add --update --no-cache \
    curl

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

# Expose the port your application will run on
EXPOSE 8080

# Command to run your application
ENTRYPOINT ["java", "-jar", "cat.jar"]