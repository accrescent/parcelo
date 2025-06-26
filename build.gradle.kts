// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.protobuf) apply false
}
