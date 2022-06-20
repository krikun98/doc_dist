FROM gradle:7-jdk16 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar --no-daemon

FROM openjdk:16
EXPOSE 8080:8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/doc_dist-*-all.jar /app/doc_dist.jar
ENTRYPOINT ["java","-jar","/app/doc_dist.jar"]