plugins {
    kotlin("jvm") version "1.8.22" apply false
    kotlin("plugin.serialization") version "1.9.0" apply false
    id("com.github.node-gradle.node") version "5.0.0" apply false
    id("com.google.protobuf") version "0.9.3" apply false
    id("io.ktor.plugin") version "2.3.1" apply false
}

tasks.register("ci") {
    dependsOn(":console:ci", ":repository:build")
}
