// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.android.xml

import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import arrow.core.None
import arrow.core.Option

fun unqualifiedName(localName: String): XmlExpandedName {
    return XmlExpandedName(None, AsciiNcName.fromUString(localName.u).unwrap())
}

fun unqualifiedAttribute(
    localName: String,
    value: ResourceValue,
    resourceId: Option<ResourceId> = None,
): XmlAttribute {
    return XmlAttribute(XmlAttributeId(unqualifiedName(localName), resourceId), value)
}
