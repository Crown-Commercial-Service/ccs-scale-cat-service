FROM maven:latest as build

RUN mkdir -p /tmp/build

WORKDIR /tmp/build

COPY . /tmp/build

# Use an official OpenJDK runtime as a base image
FROM amazoncorretto:23

# TEMP
RUN mvn -version
RUN java -version

RUN mvn clean install
RUN mvn package

RUN mkdir /app

# Copy the application JAR file and external configuration files
COPY --from=build /tmp/build/target/ccs-scale-cat-service-*.jar /app/cat.jar

RUN groupadd ujava; \
    useradd -m -g ujava -c ujava ujava; \
    chown -R ujava:ujava /app

USER ujava

# Set the working directory inside the container
WORKDIR /app

# Specify the profile to be used when running the application
ENV SPRING_PROFILES_ACTIVE=prod

# Expose the port your application will run on
EXPOSE 8080

# Command to run your application
CMD ["java", "-jar", "cat.jar"]
