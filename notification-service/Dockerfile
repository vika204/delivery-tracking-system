FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

COPY tracking-api/pom.xml tracking-api/pom.xml
RUN mvn -B -f tracking-api/pom.xml dependency:go-offline

COPY tracking-api/src tracking-api/src
RUN mvn -B -f tracking-api/pom.xml clean install -DskipTests

COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -B -f notification-service/pom.xml dependency:go-offline

COPY notification-service/src notification-service/src
RUN mvn -B -f notification-service/pom.xml clean package -DskipTests

RUN java -Djarmode=tools \
    -jar notification-service/target/*.jar \
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