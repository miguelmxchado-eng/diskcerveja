FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
COPY schemas ./schemas
RUN apk add --no-cache maven \
 && mvn -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /src/target/emissor-fiscal-*.jar app.jar
COPY --from=build /src/schemas ./schemas
ENV SCHEMAS_PATH=/app/schemas
ENV SERVER_PORT=8090
EXPOSE 8090
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
