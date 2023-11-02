// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import com.github.gradle.node.npm.task.NpxTask

val exposedVersion: String by project
val flywayVersion: String by project
val fourkomaVersion: String by project
val githubApiVersion: String by project
val jobrunrVersion: String by project
val koinVersion: String by project
val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val sqliteVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.node-gradle.node")
    id("io.ktor.plugin")
}

group = "app.accrescent"
version = "0.0.0"

application {
    mainClass.set("app.accrescent.parcelo.console.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

node {
    nodeProjectDir.set(file("${project.projectDir}/frontend"))
}

task("buildFrontendDebug", NpxTask::class) {
    dependsOn(tasks.npmInstall)
    command.set("ng")
    args.set(listOf("build", "--configuration", "development"))
    inputs.files(
        "package.json",
        "package-lock.json",
        "angular.json",
        "tsconfig.json",
        "tsconfig.app.json",
    )
    inputs.dir("${project.projectDir}/frontend/src")
    inputs.dir(fileTree("${project.projectDir}/frontend/node_modules").exclude(".cache"))
    outputs.dir("dist")
}

task("buildFrontendRelease", NpxTask::class) {
    dependsOn(tasks.npmInstall)
    command.set("ng")
    args.set(listOf("build"))
    inputs.files(
        "package.json",
        "package-lock.json",
        "angular.json",
        "tsconfig.json",
        "tsconfig.app.json",
    )
    inputs.dir("${project.projectDir}/frontend/src")
    inputs.dir(fileTree("${project.projectDir}/frontend/node_modules").exclude(".cache"))
    outputs.dir("dist")
}

task("ci") {
    dependsOn(tasks.build, "buildFrontendRelease", "npm_run_lint")
}

dependencies {
    implementation(project(":apksparser"))
    implementation("cc.ekblad:4koma:$fourkomaVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-resources:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jobrunr:jobrunr:$jobrunrVersion")
    implementation("org.kohsuke:github-api:$githubApiVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
