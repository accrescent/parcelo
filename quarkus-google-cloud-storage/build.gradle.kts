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
    implementation(libs.log4j.api)
    implementation(libs.log4j1)
    api(libs.google.cloud.storage)
    implementation(libs.quarkus.core)
    kapt(libs.quarkus.extension.processor)
}

quarkusExtension {
    deploymentModule = "quarkus-google-cloud-storage-deployment"
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
        javaParameters = true

        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
