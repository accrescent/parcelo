// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "testdata"

include(":android-app-low-target-sdk")
include(":android-app-signing")
include(":android-app-valid")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
pluginManagement {
    includeBuild("../build-logic")
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
