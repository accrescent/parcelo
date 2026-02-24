// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

sealed class AndroidManifestParseError {
    data object MalformedManifest : AndroidManifestParseError()
    data object MissingAppId : AndroidManifestParseError()
    data class AppIdTooLong(val maxLength: Int, val actualLength: Int) : AndroidManifestParseError()
    data object InvalidAppIdFormat : AndroidManifestParseError()
    data object MissingVersionCode : AndroidManifestParseError()
    data class VersionCodeTooSmall(
        val minValue: Int,
        val actualValue: Int,
    ) : AndroidManifestParseError()

    data object ApplicationNotDefined : AndroidManifestParseError()
    data object PermissionNameNotDefined : AndroidManifestParseError()
}
