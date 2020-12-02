FROM alpine:latest as START
WORKDIR /app
RUN apk add git -f
RUN git clone https://github.com/jc-benchmarkcorp/spring-petclinic.git

FROM maven:3.6.3-jdk-11-slim as BUILD
WORKDIR /app
COPY --from=START /app/spring-petclinic /app
RUN mvn install

FROM openjdk:16-slim
WORKDIR /app
ARG JAR=spring-petclinic-2.3.0.BUILD-SNAPSHOT.jar
COPY --from=BUILD /app/target/$JAR /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]