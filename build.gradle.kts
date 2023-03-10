val androidToolsVersion: String by project
val apksigVersion: String by project
val exposedVersion: String by project
val h2Version: String by project
val ktorVersion: String by project
val kotlinVersion: String by project
val protobufVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    id("com.google.protobuf") version "0.9.2"
    id("io.ktor.plugin") version "2.2.4"
}

group = "app.accrescent"
version = "0.0.0"

application {
    mainClass.set("app.accrescent.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.apkparser:apkanalyzer:$androidToolsVersion")
    implementation("com.android.tools:common:$androidToolsVersion")
    implementation("com.android.tools:sdk-common:$androidToolsVersion")
    implementation("com.android.tools.build:apksig:$apksigVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-resources:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}