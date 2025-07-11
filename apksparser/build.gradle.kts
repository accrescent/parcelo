// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    id(libs.plugins.java.library.get().pluginId)
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)

    explicitApi()
}

dependencies {
    implementation(libs.apkanalyzer)
    implementation(libs.apksig)
    api(libs.bundletool)
    implementation(libs.jackson.xml)
    implementation(libs.jackson.kotlin)
    api(libs.protobuf)
}
