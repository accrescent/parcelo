// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core.text

import app.accrescent.server.parcelo.core.unwrap

val String.u: UString
    get() = UString.fromString(this).unwrap()
