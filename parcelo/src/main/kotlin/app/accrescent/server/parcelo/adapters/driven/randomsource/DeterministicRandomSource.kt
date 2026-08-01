// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.adapters.driven.randomsource

import app.accrescent.server.parcelo.core.PositiveLong
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSource
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSourceError
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSourceResult
import arrow.core.Either
import java.util.Random

private const val RNG_SEED = 0L

class DeterministicRandomSource : RandomSource() {
    private val rng = Random(RNG_SEED)

    override fun fillRandomBytes(bytes: ByteArray): RandomSourceResult<Unit> {
        return Either.Right(rng.nextBytes(bytes))
    }

    override fun randomLong(): RandomSourceResult<Long> {
        return Either.Right(rng.nextLong())
    }

    override fun randomPositiveLong(upperBound: PositiveLong): RandomSourceResult<PositiveLong> {
        val rawValue = rng.nextLong(upperBound.value)

        return PositiveLong
            .new(rawValue)
            .toEither { RandomSourceError }
    }
}
