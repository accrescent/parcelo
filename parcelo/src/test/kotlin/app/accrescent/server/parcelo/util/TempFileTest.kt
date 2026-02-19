// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.exists

class TempFileTest {
    @Test
    fun deletedOnceClosed() {
        val path = TempFile(Path(System.getProperty("java.io.tmpdir"))).use { it.path }

        assertFalse(path.exists())
    }
}
