#Build the selected microservice using Maven and JDK 17.
FROM maven:3.9-eclipse-temurin-17 AS build

ARG SERVICE=auth-service

WORKDIR /build

#Copy only the selected service's production files.
COPY services/${SERVICE}/pom.xml ./pom.xml
COPY services/${SERVICE}/src/main ./src/main

#Cache Maven dependencies and package without compiling or running tests.
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress \
    -Dmaven.test.skip=true \
    package

#Run the packaged application using Java 17.
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

#Use Bahrain time for appointment rules and scheduled jobs.
ENV TZ=Asia/Bahrain
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Bahrain"

#Create an application user and writable profile-image directory.
RUN groupadd --gid 10001 homehub \
    && useradd --uid 10001 --gid homehub \
       --no-create-home --shell /usr/sbin/nologin homehub \
    && mkdir -p /app/uploads \
    && chown -R homehub:homehub /app

#Copy the executable Spring Boot JAR.
COPY --from=build --chown=homehub:homehub \
    /build/target/*.jar /app/app.jar

USER homehub

#Document the default ports used by the three services.
EXPOSE 8081 8082 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]