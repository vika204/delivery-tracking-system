FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /workspace

COPY web-problems/pom.xml web-problems/pom.xml
COPY web-problems/src web-problems/src
RUN mvn -B -f web-problems/pom.xml clean install -DskipTests

COPY auth-service/pom.xml auth-service/pom.xml
RUN mvn -B -f auth-service/pom.xml dependency:go-offline

COPY auth-service/src auth-service/src
RUN mvn -B -f auth-service/pom.xml clean package -DskipTests

RUN java -Djarmode=tools -jar auth-service/target/*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]