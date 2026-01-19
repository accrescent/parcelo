// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import app.accrescent.parcelo.build.ApkSetAttr
import app.accrescent.parcelo.build.ApkSetNameAttr
import build.buf.gradle.BUF_BINARY_CONFIGURATION_NAME
import com.android.build.api.attributes.BuildTypeAttr
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.buf)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.quarkus)
}

// Configuration for defining dependencies on release-mode APK set project outputs
val testApkSets by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes.attribute(ApkSetAttr.ATTRIBUTE, objects.named(ApkSetAttr::class, ApkSetAttr.PRESENT))
    attributes.attribute(BuildTypeAttr.ATTRIBUTE, objects.named(BuildTypeAttr::class, "release"))
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom)) {
        // If a dependency such as protovalidate pulls in a newer protobuf-java runtime version, we
        // need to use it so that its gencode is compatible with the server's protobuf runtime
        exclude("com.google.protobuf", "protobuf-java")
    }
    implementation(project(":quarkus-google-cloud-pubsub"))
    implementation(project(":quarkus-google-cloud-storage"))
    implementation(project(":quarkus-minio"))
    implementation(libs.apkanalyzer)
    implementation(libs.apksig)
    implementation(libs.arrow.core)
    implementation(libs.bucket4j.postgresql)
    implementation(libs.bundletool)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protovalidate)
    implementation(libs.quarkus.awt)
    implementation(libs.quarkus.flyway)
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.hibernate.validator)
    implementation(libs.quarkus.jaxb)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.kotlin)
    implementation(libs.quarkus.mailer)
    implementation(libs.quarkus.oidc)
    implementation(libs.quarkus.qute)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.scheduler)
    testImplementation(libs.awaitility)
    testImplementation(libs.htmlunit)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testApkSets(project(":testdata:android-app-valid"))
    testApkSets(project(":testdata:android-app-valid2"))
    testApkSets(project(":testdata:android-app-valid3"))
    testApkSets(project(":testdata:android-app-valid4"))
    testApkSets(project(":testdata:android-app-valid5"))
}

group = "app.accrescent.server"
version = "0.15.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        javaParameters = true

        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

// We need the Buf Gradle plugin only for the Buf CLI binary, and Gradle emits errors regarding task
// dependencies including these tasks, so disable them.
tasks.bufFormatCheck {
    enabled = false
}
tasks.bufLint {
    enabled = false
}

tasks.register<Exec>("downloadAppPublishingApiProtos") {
    inputs.property("app.accrescent.server.parcelo.app-publishing-api-version", libs.versions.app.publishing.api)
    outputs.dir("$projectDir/src/main/proto/accrescent")

    val bufExecutable = configurations.getByName(BUF_BINARY_CONFIGURATION_NAME).singleFile
    if (!bufExecutable.canExecute()) {
        bufExecutable.setExecutable(true)
    }

    val appPublishingApiVersion = inputs.properties["app.accrescent.server.parcelo.app-publishing-api-version"]

    commandLine(
        bufExecutable.absolutePath,
        "export",
        "buf.build/accrescent/app-publishing-api:$appPublishingApiVersion",
        "--output",
        "$projectDir/src/main/proto/",
    )

    // Remove buf/validate/validate.proto so that Quarkus doesn't generate classes which conflict
    // with those defined in our protovalidate dependency
    doLast {
        file("$projectDir/src/main/proto/buf").deleteRecursively()
    }
}
tasks.register<Exec>("downloadAppStoreApiProtos") {
    inputs.property("app.accrescent.server.parcelo.app-store-api-version", libs.versions.appstore.api)
    outputs.dir("$projectDir/src/main/proto/accrescent")

    val bufExecutable = configurations.getByName(BUF_BINARY_CONFIGURATION_NAME).singleFile
    if (!bufExecutable.canExecute()) {
        bufExecutable.setExecutable(true)
    }

    val appStoreApiVersion = inputs.properties["app.accrescent.server.parcelo.app-store-api-version"]

    commandLine(
        bufExecutable.absolutePath,
        "export",
        "buf.build/accrescent/appstore-api:$appStoreApiVersion",
        "--output",
        "$projectDir/src/main/proto/",
    )

    // Remove buf/validate/validate.proto so that Quarkus doesn't generate classes which conflict
    // with those defined in our protovalidate dependency
    doLast {
        file("$projectDir/src/main/proto/buf").deleteRecursively()
    }
}
tasks.register("downloadProtos") {
    dependsOn(tasks.getByName("downloadAppPublishingApiProtos"))
    dependsOn(tasks.getByName("downloadAppStoreApiProtos"))
}
tasks.quarkusGenerateCode {
    dependsOn(tasks.getByName("downloadProtos"))
}

// Workaround for https://github.com/quarkusio/quarkus/issues/50486
sourceSets {
    main {
        java {
            srcDirs("build/classes/java/quarkus-generated-sources/grpc")
        }
    }
}

// Make our integration tests depend on our generated test data
tasks.quarkusIntTest {
    inputs.files(testApkSets)

    doFirst {
        testApkSets.incoming.artifacts.forEach { artifact ->
            val name = artifact.variant.attributes.getAttribute(ApkSetNameAttr.ATTRIBUTE)!!
            systemProperty("testdata.apkset.$name.path", artifact.file.absolutePath)
        }
    }
}
