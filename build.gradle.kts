import com.github.gradle.node.npm.task.NpxTask

val androidToolsVersion: String by project
val apksigVersion: String by project
val exposedVersion: String by project
val githubApiVersion: String by project
val h2Version: String by project
val jacksonVersion: String by project
val koinVersion: String by project
val ktorVersion: String by project
val kotlinVersion: String by project
val protobufVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.20"
    id("com.github.node-gradle.node") version "4.0.0"
    id("com.google.protobuf") version "0.9.2"
    id("io.ktor.plugin") version "2.3.0"
}

group = "app.accrescent"
version = "0.0.0"

application {
    mainClass.set("app.accrescent.parcelo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

node {
    nodeProjectDir.set(file("${project.projectDir}/frontend"))
}

repositories {
    google()
    mavenCentral()
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
    implementation("com.android.tools.apkparser:apkanalyzer:$androidToolsVersion")
    implementation("com.android.tools.build:apksig:$apksigVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-resources:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.kohsuke:github-api:$githubApiVersion")
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
