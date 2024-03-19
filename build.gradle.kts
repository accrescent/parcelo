// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    kotlin("jvm") version "1.9.23" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("io.ktor.plugin") version "2.3.8" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false
}
