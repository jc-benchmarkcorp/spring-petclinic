FROM alpine:latest as START
WORKDIR /app
RUN apk add git -f
RUN git clone https://github.com/jc-benchmarkcorp/spring-petclinic.git

FROM maven:alpine as BUILD
WORKDIR /app
COPY --from=START /app/spring-petclinic /app
RUN mvn install

FROM openjdk:8-alpine
WORKDIR /app
ARG JAR=spring-petclinic-2.3.0.BUILD-SNAPSHOT.jar
COPY --from=BUILD /app/target/$JAR /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]