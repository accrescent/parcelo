# Copyright 2024 Logan Magee
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM eclipse-temurin:17.0.13_11-jdk AS cache

ENV GRADLE_USER_HOME=/cache
COPY apksparser/build.gradle.kts /app/apksparser/
COPY console/build.gradle.kts /app/console/
COPY gradle/ /app/gradle/
COPY build.gradle.kts gradle.properties gradlew settings.gradle.kts /app/
WORKDIR /app
RUN ./gradlew clean build --no-daemon

FROM eclipse-temurin:17.0.13_11-jdk AS builder

ARG DEBIAN_FRONTEND=noninteractive
ARG BUILD_SYSTEM=linux-x86_64
ARG PROTOBUF_VERSION=28.2

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
RUN curl -Lo protoc.zip \
    https://github.com/protocolbuffers/protobuf/releases/download/v$PROTOBUF_VERSION/protoc-$PROTOBUF_VERSION-$BUILD_SYSTEM.zip \
    && unzip -o protoc.zip -d /usr/local bin/protoc \
    && unzip -o protoc.zip -d /usr/local 'include/*' \
    && rm -f protoc.zip

WORKDIR /build
COPY --from=cache /cache /root/.gradle
COPY . /build
RUN ./gradlew clean buildFatJar --no-daemon

FROM eclipse-temurin:17.0.13_11-jre

WORKDIR /app
COPY --from=builder /build/console/build/libs/console-all.jar parcelo-console.jar
RUN groupadd -r parcelo-console && useradd --no-log-init -r -g parcelo-console parcelo-console
USER parcelo-console

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "parcelo-console.jar"]
