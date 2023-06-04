val androidToolsVersion: String by project
val apksigVersion: String by project
val jacksonVersion: String by project
val kotlinVersion: String by project
val protobufVersion: String by project
val semverVersion: String by project

plugins {
    `java-library`
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    api("com.github.zafarkhaja:java-semver:$semverVersion")
    implementation("com.android.tools.apkparser:apkanalyzer:$androidToolsVersion")
    implementation("com.android.tools.build:apksig:$apksigVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
}

kotlin {
    explicitApi()
}
