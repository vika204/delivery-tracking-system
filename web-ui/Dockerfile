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

RUN java -Djarmode=tools \
    -jar web-ui/target/*.jar \
    extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
