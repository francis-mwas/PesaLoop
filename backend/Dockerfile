# ── Stage 1: build ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

# Install Maven
RUN apk add --no-cache maven

WORKDIR /workspace

# Copy pom first — layer caches dependency downloads separately from source
COPY pom.xml ./backend/

WORKDIR /workspace/backend
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S pesaloop && adduser -S pesaloop -G pesaloop

WORKDIR /app

COPY --from=build /workspace/backend/target/pesaloop-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=docker"

USER pesaloop
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]