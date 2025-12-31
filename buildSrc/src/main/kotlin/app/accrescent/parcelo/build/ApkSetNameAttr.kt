// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.build

import org.gradle.api.attributes.Attribute

interface ApkSetNameAttr {
    companion object {
        val ATTRIBUTE = Attribute.of("app.accrescent.parcelo.build.apk-set-name", String::class.java)
    }
}
