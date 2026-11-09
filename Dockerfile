# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
# Dependências primeiro: a camada fica em cache enquanto o pom.xml não mudar
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ---------- Runtime ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/uploads && chown spring:spring /app/uploads
COPY --from=build /app/target/*.jar app.jar
USER spring:spring
ENV TZ=America/Sao_Paulo
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
