// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class ParceloRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("parcelo")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::DirectPreparedStatementExecuteUpdate,
            ::JavaBase64Usage,
            ::TruncatingIntegerConversion,
            ::UnsafeJdbcResultSetMethodCall,
            ::UnusedEitherValue,
        ),
    )
}
