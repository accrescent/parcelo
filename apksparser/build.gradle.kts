// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    id(libs.plugins.java.library.get().pluginId)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.apkanalyzer)
    implementation(libs.apksig)
    implementation(libs.jackson.xml)
    implementation(libs.jackson.kotlin)
    api(libs.protobuf)
}

kotlin {
    explicitApi()
}
