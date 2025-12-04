// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

sealed class ApkSetParseResult {
    data class Ok(val apkSet: ApkSet) : ApkSetParseResult()
    data object InvalidFormat : ApkSetParseResult()
    data object IoError : ApkSetParseResult()
    sealed class RequirementError : ApkSetParseResult() {
        data object NoModernSignature : RequirementError()
        data object SignedWithDebugCert : RequirementError()
        data object SignedWithMultipleCerts : RequirementError()
        data object TestOnly : RequirementError()
        data object Debuggable : RequirementError()
        data object Missing64BitCode : RequirementError()
        data object LowTargetSdk : RequirementError()
    }
}
