plugins {
    kotlin("jvm") version "1.9.0" apply false
    kotlin("plugin.serialization") version "1.9.0" apply false
    id("com.github.node-gradle.node") version "5.0.0" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("io.ktor.plugin") version "2.3.2" apply false
    id("org.sonarqube") version "4.3.0.3225"
}

sonar {
    properties {
        property("sonar.projectKey", "accrescent_parcelo")
        property("sonar.organization", "accrescent")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

tasks.register("ci") {
    dependsOn(":console:ci", ":repository:build")
}
