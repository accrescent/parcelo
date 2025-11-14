// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    implementation(platform(libs.google.cloud.libraries.bom))
    implementation(platform(libs.quarkus.bom))
    implementation(libs.google.cloud.storage)
    implementation(libs.quarkus.devservices.common)
    implementation(libs.testcontainers)
    kapt(libs.quarkus.extension.processor)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
        javaParameters = true
    }
}
