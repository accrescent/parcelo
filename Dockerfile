# Copyright 2024 Logan Magee
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM eclipse-temurin:21.0.8_9-jdk AS cache

ENV GRADLE_USER_HOME=/cache
COPY apksparser/build.gradle.kts /app/apksparser/
COPY console/build.gradle.kts /app/console/
COPY gradle/ /app/gradle/
COPY build.gradle.kts gradle.properties gradlew settings.gradle.kts /app/
WORKDIR /app
RUN ./gradlew clean build --no-daemon

FROM eclipse-temurin:21.0.8_9-jdk AS builder

WORKDIR /build
COPY --from=cache /cache /root/.gradle
COPY . /build
RUN ./gradlew clean buildFatJar --no-daemon

FROM eclipse-temurin:21.0.8_9-jre

WORKDIR /app
COPY --from=builder /build/console/build/libs/console-all.jar parcelo-console.jar
RUN groupadd -r parcelo-console && useradd --no-log-init -r -g parcelo-console parcelo-console
USER parcelo-console

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "parcelo-console.jar"]
