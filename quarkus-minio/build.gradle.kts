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
    implementation(platform(libs.quarkus.bom))
    api(libs.minio)
    implementation(libs.quarkus.core)
    kapt(libs.quarkus.extension.processor)
}

quarkusExtension {
    deploymentModule = "quarkus-minio-deployment"
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        javaParameters = true
    }
}
