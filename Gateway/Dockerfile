FROM openjdk:17
EXPOSE 8060
ADD target/APIGatewayService-0.0.1-SNAPSHOT.jar Gateway.jar
ENTRYPOINT ["java", "-jar","Gateway.jar"]