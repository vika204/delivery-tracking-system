FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

COPY tracking-api/pom.xml tracking-api/pom.xml
COPY tracking-api/src tracking-api/src
RUN mvn -B -f tracking-api/pom.xml clean install -DskipTests

COPY tracking-grpc-client/pom.xml tracking-grpc-client/pom.xml
COPY tracking-grpc-client/src tracking-grpc-client/src
RUN mvn -B -f tracking-grpc-client/pom.xml clean install -DskipTests

COPY web-problems/pom.xml web-problems/pom.xml
COPY web-problems/src web-problems/src
RUN mvn -B -f web-problems/pom.xml clean install -DskipTests

COPY web-ui/pom.xml web-ui/pom.xml
RUN mvn -B -f web-ui/pom.xml dependency:go-offline
COPY web-ui/src web-ui/src
RUN mvn -B -f web-ui/pom.xml clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=builder /workspace/web-ui/target/web-ui-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
