// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.quarkus.extension)
}

dependencies {
    api(platform(libs.google.cloud.libraries.bom))
    implementation(platform(libs.quarkus.bom))
    api(libs.google.cloud.pubsub)
    implementation(libs.quarkus.core)
    kapt(libs.quarkus.extension.processor)
}

quarkusExtension {
    deploymentModule = "quarkus-google-cloud-pubsub-deployment"
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
