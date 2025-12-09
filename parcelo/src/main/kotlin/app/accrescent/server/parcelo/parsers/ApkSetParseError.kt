// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

sealed class ApkSetParseError {
    data object InvalidFormat : ApkSetParseError()
    data object IoError : ApkSetParseError()
    sealed class RequirementError : ApkSetParseError() {
        data object NoModernSignature : RequirementError()
        data object SignedWithDebugCert : RequirementError()
        data object SignedWithMultipleCerts : RequirementError()
        data object TestOnly : RequirementError()
        data object Debuggable : RequirementError()
        data object Missing64BitCode : RequirementError()
        data object LowTargetSdk : RequirementError()
    }
}
