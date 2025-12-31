FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace/app

# Copy parent pom first
COPY pom.xml /workspace/app/parent-pom.xml

# Copy service-specific files
COPY portfolio-service/pom.xml .
COPY portfolio-service/src src

# Install parent POM and build service
RUN mvn -f /workspace/app/parent-pom.xml install -N -DskipTests && \
    mvn clean package -DskipTests
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
ENTRYPOINT ["java","-cp","app:app/lib/*","com.wealthmanagement.portfolio.PortfolioServiceApplication"]
