// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

/**
 * Metadata about an app to be published for clients.
 *
 * This class contains two fields: the legacy JSON metadata and the future APK set metadata. It is
 * intended as a transitory transitional component in migrating to support the directory service.
 *
 * @property jsonMetadata the legacy JSON app metadata
 * @property apkSetMetadata a BuildApksResult protobuf object extracted from the APK set
 */
class AppMetadata(val jsonMetadata: ByteArray, val apkSetMetadata: ByteArray)
