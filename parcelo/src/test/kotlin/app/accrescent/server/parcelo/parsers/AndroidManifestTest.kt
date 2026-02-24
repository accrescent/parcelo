// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

import arrow.core.left
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class AndroidManifestTest {
    private val androidManifestWithNoncontiguousPermissions =
        loadResource("android-manifest-with-noncontiguous-permissions.xml")
    private val androidManifestWithoutDebuggable =
        loadResource("android-manifest-without-debuggable.xml")
    private val androidManifestWithoutTestOnly =
        loadResource("android-manifest-without-test-only.xml")
    private val androidManifestWithoutMinSdkVersion =
        loadResource("android-manifest-without-min-sdk-version.xml")
    private val androidManifestWithoutTargetSdkVersion =
        loadResource("android-manifest-without-target-sdk-version.xml")

    @Test
    fun parseWithNoncontiguousPermissionsIncludesAllPermissions() {
        val manifest = AndroidManifest
            .parse(androidManifestWithNoncontiguousPermissions)
            .getOrNull()!!

        assertEquals(
            listOf(
                UsesPermission("android.permission.CAMERA", null),
                UsesPermission("android.permission.ACCESS_COARSE_LOCATION", null),
            ),
            manifest.usesPermissions,
        )
    }

    @ParameterizedTest
    @MethodSource("genParamsForParseRejectsInvalidManifest")
    fun parseRejectsInvalidManifest(params: ParseRejectsInvalidManifestParams) {
        assertEquals(
            params.expectedError.left(),
            AndroidManifest.parse(loadResource(params.resourcePath)),
        )
    }

    @Test
    fun parseDefaultsDebuggableToFalse() {
        val manifest = AndroidManifest
            .parse(androidManifestWithoutDebuggable)
            .getOrNull()!!

        assertFalse(manifest.application.debuggable)
    }

    @Test
    fun parseDefaultsTestOnlyToFalse() {
        val manifest = AndroidManifest
            .parse(androidManifestWithoutTestOnly)
            .getOrNull()!!

        assertFalse(manifest.application.testOnly)
    }

    @Test
    fun parseDefaultsMinSdkVersionToOne() {
        val manifest = AndroidManifest
            .parse(androidManifestWithoutMinSdkVersion)
            .getOrNull()!!

        assertEquals(1, manifest.usesSdk?.minSdkVersion)
    }

    @Test
    fun parseDefaultsTargetSdkVersionToMinSdkVersion() {
        val manifest = AndroidManifest
            .parse(androidManifestWithoutTargetSdkVersion)
            .getOrNull()!!

        assertEquals(33, manifest.usesSdk?.targetSdkVersion)
    }

    private fun loadResource(path: String): String {
        return javaClass
            .classLoader
            .getResourceAsStream(path)!!
            .use { resourceStream -> resourceStream.bufferedReader().use { it.readText() } }
    }

    private companion object {
        @JvmStatic
        private fun genParamsForParseRejectsInvalidManifest(): List<ParseRejectsInvalidManifestParams> {
            return listOf(
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-with-too-long-app-id.xml",
                    expectedError = AndroidManifestParseError.AppIdTooLong(128, 129),
                ),
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-with-invalid-app-id-format.xml",
                    expectedError = AndroidManifestParseError.InvalidAppIdFormat,
                ),
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-with-too-small-version-code.xml",
                    expectedError = AndroidManifestParseError.VersionCodeTooSmall(1, 0),
                ),
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-without-package.xml",
                    expectedError = AndroidManifestParseError.MissingAppId,
                ),
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-without-version-code.xml",
                    expectedError = AndroidManifestParseError.MissingVersionCode,
                ),
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-without-application.xml",
                    expectedError = AndroidManifestParseError.ApplicationNotDefined,
                ),
                ParseRejectsInvalidManifestParams(
                    resourcePath = "android-manifest-with-unnamed-permission.xml",
                    expectedError = AndroidManifestParseError.PermissionNameNotDefined,
                ),
            )
        }
    }
}

data class ParseRejectsInvalidManifestParams(
    val resourcePath: String,
    val expectedError: AndroidManifestParseError,
)
