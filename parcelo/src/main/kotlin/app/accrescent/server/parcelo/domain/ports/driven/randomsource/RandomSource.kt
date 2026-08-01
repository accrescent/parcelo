// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.randomsource

import app.accrescent.server.parcelo.core.PositiveLong
import arrow.core.Either

typealias RandomSourceResult<T> = Either<RandomSourceError, T>

data object RandomSourceError

abstract class RandomSource {
    abstract fun fillRandomBytes(bytes: ByteArray): RandomSourceResult<Unit>
    abstract fun randomLong(): RandomSourceResult<Long>
    abstract fun randomPositiveLong(upperBound: PositiveLong): RandomSourceResult<PositiveLong>
}
