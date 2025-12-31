// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.build

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

interface ApkSetAttr : Named {
    companion object {
        val ATTRIBUTE = Attribute.of(ApkSetAttr::class.java)
        const val PRESENT = "present"
    }
}
