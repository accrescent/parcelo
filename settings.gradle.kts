// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "parcelo"

include("apksparser", "console")

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
