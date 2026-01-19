// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "parcelo"

include(
    "parcelo",
    "quarkus-google-cloud-pubsub",
    "quarkus-google-cloud-pubsub-deployment",
    "quarkus-google-cloud-pubsub-devservices",
    "quarkus-google-cloud-pubsub-spi",
    "quarkus-google-cloud-storage",
    "quarkus-google-cloud-storage-deployment",
    "quarkus-google-cloud-storage-devservices",
    "quarkus-minio",
    "quarkus-minio-deployment",
    "quarkus-minio-devservices",
    "testdata:android-app-valid",
    "testdata:android-app-valid2",
    "testdata:android-app-valid3",
    "testdata:android-app-valid4",
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
