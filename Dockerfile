FROM openjdk:8-jdk-alpine
ARG JAR_FILE=target/*.jar

RUN apk add --update make git curl curl-dev openssh python3 && \
    git clone https://github.com/spring-projects/spring-petclinic.git &&\
    cd spring-petclinic &&\
    ./mvnw package

COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]