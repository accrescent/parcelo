// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "parcelo"

include(
    "parcelo",
    "quarkus-google-cloud-storage",
    "quarkus-google-cloud-storage-deployment",
    "quarkus-google-cloud-storage-devservices",
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            name = "buf"
            url = uri("https://buf.build/gen/maven")
        }
        maven {
            name = "confluent"
            url = uri("https://packages.confluent.io/maven")
        }
    }
}
