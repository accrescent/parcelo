// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.Panache
import io.quarkus.narayana.jta.TransactionRunnerOptions

fun <T> TransactionRunnerOptions.call(
    isolationLevel: TransactionIsolationLevel,
    block: () -> T,
): T {
    return call {
        Panache
            .getEntityManager()
            .createNativeQuery("SET TRANSACTION ISOLATION LEVEL ${isolationLevel.sql}")
            .executeUpdate()

        block()
    }
}
