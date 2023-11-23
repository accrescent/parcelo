// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    kotlin("jvm") version "1.9.20" apply false
    kotlin("plugin.serialization") version "1.9.21" apply false
    id("com.github.node-gradle.node") version "7.0.1" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("io.ktor.plugin") version "2.3.6" apply false
}

tasks.register("ci") {
    dependsOn(":console:ci", ":repository:build")
}
