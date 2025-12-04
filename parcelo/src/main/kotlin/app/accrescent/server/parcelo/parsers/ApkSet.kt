// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

import app.accrescent.server.parcelo.util.TempFile
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import com.android.bundle.Commands
import com.android.bundle.Targeting
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.outputStream

private const val DEBUG_CERT_PRINCIPAL_NAME = "C=US,O=Android,CN=Android Debug"
private const val MIN_TARGET_SDK = 35
private const val TOC_PATH = "toc.pb"

class ApkSet private constructor(
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val targetSdk: Int,
    val signingCert: X509Certificate,
    val buildApksResult: Commands.BuildApksResult,
) {
    companion object {
        /**
         * Parses an APK set into its metadata.
         *
         * This parser imposes restrictions specific to Accrescent which can be found in
         * [our documentation](https://accrescent.app/docs/guide/appendix/requirements.html) and
         * performs some other basic checks to ensure the app is installable.
         */
        fun parse(path: Path, tmpApkDir: Path): ApkSetParseResult {
            try {
                ZipFile(path.toFile())
            } catch (_: ZipException) {
                return ApkSetParseResult.InvalidFormat
            } catch (_: IOException) {
                return ApkSetParseResult.IoError
            }.use { zipFile ->
                // Parse the table of contents
                val tableOfContents = zipFile
                    .getEntry(TOC_PATH)
                    ?: return ApkSetParseResult.InvalidFormat
                val buildApksResult = try {
                    zipFile
                        .getInputStream(tableOfContents)
                        .use { Commands.BuildApksResult.parseFrom(it) }
                } catch (_: ZipException) {
                    return ApkSetParseResult.InvalidFormat
                } catch (_: IOException) {
                    return ApkSetParseResult.IoError
                }

                // Check compliance with 64-bit requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#native-code
                for (variant in buildApksResult.variantList) {
                    val supportedAbis = variant.targeting.abiTargeting.valueList.map { it.alias }
                    val arm32Supported = supportedAbis.contains(Targeting.Abi.AbiAlias.ARMEABI_V7A)
                    val arm64Supported = supportedAbis.contains(Targeting.Abi.AbiAlias.ARM64_V8A)
                    val x86Supported = supportedAbis.contains(Targeting.Abi.AbiAlias.X86)
                    val x8664Supported = supportedAbis.contains(Targeting.Abi.AbiAlias.X86_64)

                    if ((arm32Supported && !arm64Supported) || (x86Supported && !x8664Supported)) {
                        return ApkSetParseResult.RequirementError.Missing64BitCode
                    }
                }

                // Check compliance of individual APKs
                val apkPaths = buildApksResult
                    .variantList
                    .flatMap { it.apkSetList }
                    .flatMap { it.apkDescriptionList }
                    .map { it.path }
                    // Paths can be repeated across variants, so deduplicate them here so that we
                    // don't unnecessarily process an APK more than once
                    .toSet()
                var pinnedSigningCert: X509Certificate? = null
                var pinnedVersionCode: Int? = null
                var firstEncounteredVersionName: String? = null
                var lowestEncounteredTargetSdk: Int? = null
                for (apkPath in apkPaths) {
                    val apkEntry = zipFile
                        .getEntry(apkPath)
                        ?: return ApkSetParseResult.InvalidFormat
                    try {
                        zipFile.getInputStream(apkEntry)
                    } catch (_: ZipException) {
                        return ApkSetParseResult.InvalidFormat
                    } catch (_: IOException) {
                        return ApkSetParseResult.IoError
                    }.use { apkInputStream ->
                        TempFile(tmpApkDir).use { tempApk ->
                            tempApk.path.outputStream().use { apkInputStream.copyTo(it) }

                            // Perform signature-related compliance and installability checks
                            val verificationResult = try {
                                ApkVerifier.Builder(tempApk.path.toFile()).build().verify()
                            } catch (_: IOException) {
                                return ApkSetParseResult.IoError
                            } catch (_: ApkFormatException) {
                                return ApkSetParseResult.InvalidFormat
                            }
                            when {
                                // Check that APK signature verifies to ensure the app is installable
                                //
                                // https://source.android.com/docs/security/features/apksigning/v3#verification
                                !verificationResult.isVerified ->
                                    return ApkSetParseResult.InvalidFormat

                                // Check compliance with signature scheme requirement
                                //
                                // https://accrescent.app/docs/guide/appendix/requirements.html#signature-scheme
                                !verificationResult.isVerifiedUsingModernScheme() ->
                                    return ApkSetParseResult.RequirementError.NoModernSignature

                                // Check compliance with debug certificate requirement
                                //
                                // https://accrescent.app/docs/guide/appendix/requirements.html#debug-certificate
                                verificationResult.signerCertificates.any { it.isDebug() } ->
                                    return ApkSetParseResult.RequirementError.SignedWithDebugCert

                                // Check compliance with multiple signers requirement
                                //
                                // https://accrescent.app/docs/guide/appendix/requirements.html#multiple-signers
                                verificationResult.signerCertificates.size > 1 ->
                                    return ApkSetParseResult.RequirementError.SignedWithMultipleCerts
                            }
                            // Check that all APKs are signed with the same certificate to ensure
                            // that 1) we have an accurate view of how the app is signed and 2) that
                            // the app is installable
                            //
                            // https://developer.android.com/reference/android/content/pm/PackageInstaller
                            val apkSigningCert = verificationResult.signerCertificates[0]
                            if (pinnedSigningCert == null) {
                                pinnedSigningCert = apkSigningCert
                            } else if (apkSigningCert != pinnedSigningCert) {
                                return ApkSetParseResult.InvalidFormat
                            }

                            // Perform manifest-related compliance and installability checks
                            val binaryManifest = try {
                                val randomAccessFile = RandomAccessFile(tempApk.path.toFile(), "r")
                                val buf = ApkUtils
                                    .getAndroidManifest(DataSources.asDataSource(randomAccessFile))
                                ByteArray(buf.remaining()).also { buf.get(it) }
                            } catch (_: IOException) {
                                return ApkSetParseResult.IoError
                            } catch (_: ApkFormatException) {
                                return ApkSetParseResult.InvalidFormat
                            }
                            val manifest = AndroidManifest
                                .parse(binaryManifest)
                                ?: return ApkSetParseResult.InvalidFormat
                            when {
                                // Check that the package name (application ID) of all APKs is the
                                // same to ensure the app is installable
                                //
                                // https://developer.android.com/reference/android/content/pm/PackageInstaller
                                manifest.`package` != buildApksResult.packageName ->
                                    return ApkSetParseResult.InvalidFormat

                                // Check compliance with testOnly requirement
                                //
                                // https://accrescent.app/docs/guide/appendix/requirements.html#androidtestonly
                                manifest.application.testOnly ->
                                    return ApkSetParseResult.RequirementError.TestOnly

                                // Check compliance with debuggable requirement
                                //
                                // https://accrescent.app/docs/guide/appendix/requirements.html#androiddebuggable
                                manifest.application.debuggable ->
                                    return ApkSetParseResult.RequirementError.Debuggable
                            }
                            // Check that the version code of all APKs is the same to ensure the app
                            // is installable
                            //
                            // https://developer.android.com/reference/android/content/pm/PackageInstaller
                            if (pinnedVersionCode == null) {
                                pinnedVersionCode = manifest.versionCode
                            } else if (manifest.versionCode != pinnedVersionCode) {
                                return ApkSetParseResult.InvalidFormat
                            }
                            // Save lowest encountered target SDK for target SDK compliance checking
                            val apkTargetSdk = manifest.usesSdk?.targetSdkVersion
                            val lowestTargetSdk = lowestEncounteredTargetSdk
                            if (
                                apkTargetSdk != null
                                && (lowestTargetSdk == null || apkTargetSdk < lowestTargetSdk)
                            ) {
                                lowestEncounteredTargetSdk = apkTargetSdk
                            }
                            // Save first encountered version name for informational purposes
                            if (firstEncounteredVersionName == null && manifest.versionName != null) {
                                firstEncounteredVersionName = manifest.versionName
                            }
                        }
                    }
                }

                if (
                    pinnedVersionCode == null
                    || pinnedSigningCert == null
                    || lowestEncounteredTargetSdk == null
                    || firstEncounteredVersionName == null
                ) {
                    return ApkSetParseResult.InvalidFormat
                }

                // Check compliance with target SDK requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#target-sdk
                if (lowestEncounteredTargetSdk < MIN_TARGET_SDK) {
                    return ApkSetParseResult.RequirementError.LowTargetSdk
                }

                val apkSet = ApkSet(
                    applicationId = buildApksResult.packageName,
                    versionCode = pinnedVersionCode,
                    versionName = firstEncounteredVersionName,
                    targetSdk = lowestEncounteredTargetSdk,
                    signingCert = pinnedSigningCert,
                    buildApksResult = buildApksResult,
                )
                return ApkSetParseResult.Ok(apkSet)
            }
        }
    }
}

private fun ApkVerifier.Result.isVerifiedUsingModernScheme(): Boolean {
    return isVerifiedUsingV2Scheme || isVerifiedUsingV3Scheme || isVerifiedUsingV31Scheme
}

private fun X509Certificate.isDebug(): Boolean {
    return subjectX500Principal.name == DEBUG_CERT_PRINCIPAL_NAME
}
