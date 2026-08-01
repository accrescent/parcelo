// SPDX-FileCopyrightText: © 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "parcelo"

include(
    "detekt-rules",
    "parcelo",
    "quarkus-google-cloud-pubsub",
    "quarkus-google-cloud-pubsub-deployment",
    "quarkus-google-cloud-pubsub-devservices",
    "quarkus-google-cloud-pubsub-spi",
    "quarkus-google-cloud-sql-postgres-socket-factory",
    "quarkus-google-cloud-sql-postgres-socket-factory-deployment",
    "quarkus-google-cloud-storage",
    "quarkus-google-cloud-storage-deployment",
    "quarkus-google-cloud-storage-devservices",
    "quarkus-minio",
    "quarkus-minio-deployment",
    "quarkus-minio-devservices",
    "quarkus-protovalidate",
    "quarkus-protovalidate-deployment",
)

includeBuild("testdata")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}
pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
