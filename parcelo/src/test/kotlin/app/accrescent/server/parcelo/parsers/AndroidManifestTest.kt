// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

private const val ANDROID_MANIFEST_WITH_NONCONTIGUOUS_PERMISSIONS_PATH =
    "android-manifest-with-noncontiguous-permissions.xml"

class AndroidManifestTest {
    private val androidManifestWithNoncontiguousPermissions = javaClass
        .classLoader
        .getResourceAsStream(ANDROID_MANIFEST_WITH_NONCONTIGUOUS_PERMISSIONS_PATH)!!
        .use { resourceStream -> resourceStream.bufferedReader().use { it.readText() } }

    @Test
    fun parseWithNoncontiguousPermissionsIncludesAllPermissions() {
        val permissions = AndroidManifest
            .parse(androidManifestWithNoncontiguousPermissions)
            ?.usesPermissions

        assertNotNull(permissions)
        assertEquals(
            listOf(
                UsesPermission("android.permission.CAMERA", null),
                UsesPermission("android.permission.ACCESS_COARSE_LOCATION", null),
            ),
            permissions,
        )
    }
}
