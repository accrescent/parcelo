// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

import arrow.core.Either
import arrow.core.raise.either
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Path
import java.security.cert.X509Certificate

private const val DEBUG_CERT_PRINCIPAL_NAME = "C=US,O=Android,CN=Android Debug"

class Apk private constructor(
    val manifest: AndroidManifest,
    val signingCertificate: X509Certificate,
) {
    companion object {
        fun parse(path: Path): Either<ApkSetParseError, Apk> = either {
            // Perform signature-related compliance and installability checks
            val verificationResult = try {
                ApkVerifier.Builder(path.toFile()).build().verify()
            } catch (_: IOException) {
                raise(ApkSetParseError.IoError)
            } catch (_: ApkFormatException) {
                raise(ApkSetParseError.InvalidFormat)
            }
            when {
                // Check that APK signature verifies to ensure the app is installable
                //
                // https://source.android.com/docs/security/features/apksigning/v3#verification
                !verificationResult.isVerified -> raise(ApkSetParseError.InvalidFormat)

                // Check compliance with signature scheme requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#signature-scheme
                !verificationResult.isVerifiedUsingModernScheme() ->
                    raise(ApkSetParseError.RequirementError.NoModernSignature)

                // Check compliance with debug certificate requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#debug-certificate
                verificationResult.signerCertificates.any { it.isDebug() } ->
                    raise(ApkSetParseError.RequirementError.SignedWithDebugCert)

                // Check compliance with multiple signers requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#multiple-signers
                verificationResult.signerCertificates.size > 1 ->
                    raise(ApkSetParseError.RequirementError.SignedWithMultipleCerts)
            }

            // Perform manifest-related compliance checks
            val binaryManifest = try {
                val randomAccessFile = RandomAccessFile(path.toFile(), "r")
                val buf = ApkUtils.getAndroidManifest(DataSources.asDataSource(randomAccessFile))
                ByteArray(buf.remaining()).also { buf.get(it) }
            } catch (_: IOException) {
                raise(ApkSetParseError.IoError)
            } catch (_: ApkFormatException) {
                raise(ApkSetParseError.InvalidFormat)
            }
            val manifest = AndroidManifest
                .parse(binaryManifest)
                ?: raise(ApkSetParseError.InvalidFormat)
            when {
                // Check compliance with testOnly requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#androidtestonly
                manifest.application.testOnly ->
                    raise(ApkSetParseError.RequirementError.TestOnly)

                // Check compliance with debuggable requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#androiddebuggable
                manifest.application.debuggable ->
                    raise(ApkSetParseError.RequirementError.Debuggable)
            }

            Apk(manifest, verificationResult.signerCertificates[0])
        }
    }
}

private fun ApkVerifier.Result.isVerifiedUsingModernScheme(): Boolean {
    return isVerifiedUsingV2Scheme || isVerifiedUsingV3Scheme || isVerifiedUsingV31Scheme
}

private fun X509Certificate.isDebug(): Boolean {
    return subjectX500Principal.name == DEBUG_CERT_PRINCIPAL_NAME
}
