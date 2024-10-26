// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktor)
    alias(libs.plugins.dokka)
}

group = "app.accrescent"
version = "0.14.0"

application {
    mainClass.set("app.accrescent.parcelo.console.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    // Workaround for regressed Jackson 2.18.0 being pulled in by GCS library
    implementation("com.fasterxml.jackson:jackson-bom") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.core:jackson-annotations") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.core:jackson-core") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.core:jackson-databind") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformats-text") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.module:jackson-modules-java8") {
        version { strictly("2.17.2") }
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin") {
        version { strictly("2.17.2") }
    }
    implementation(project(":apksparser"))
    implementation(libs.s3)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.github)
    implementation(platform(libs.google.cloud.bom))
    implementation(libs.google.cloud.storage)
    implementation(libs.jobrunr)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)
    implementation(libs.ktor.client)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.resources)
    implementation(libs.logback)
    implementation(libs.postgresql)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}

tasks.withType<DokkaTask>().configureEach {
    failOnWarning.set(true)

    dokkaSourceSets {
        configureEach {
            reportUndocumented.set(true)

            perPackageOption {
                // FIXME(#494): Document console and remove this exclusion
                matchingRegex.set("""app\.accrescent\.parcelo\.console(?:\.(?:data(?:\.baseline)?(?:\.net)?|routes))?""")
                reportUndocumented.set(false)
            }
        }
    }
}
