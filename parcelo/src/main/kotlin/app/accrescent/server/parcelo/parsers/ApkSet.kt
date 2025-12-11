// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

import app.accrescent.server.parcelo.util.TempFile
import app.accrescent.server.parcelo.util.apkPaths
import arrow.core.Either
import arrow.core.raise.either
import com.android.bundle.Commands
import com.android.bundle.Targeting
import java.io.IOException
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.outputStream

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
        fun parse(path: Path, tmpApkDir: Path): Either<ApkSetParseError, ApkSet> = either {
            try {
                ZipFile(path.toFile())
            } catch (_: ZipException) {
                raise(ApkSetParseError.InvalidFormat)
            } catch (_: IOException) {
                raise(ApkSetParseError.IoError)
            }.use { zipFile ->
                // Parse the table of contents
                val tableOfContents = zipFile
                    .getEntry(TOC_PATH)
                    ?: raise(ApkSetParseError.InvalidFormat)
                val buildApksResult = try {
                    zipFile
                        .getInputStream(tableOfContents)
                        .use { Commands.BuildApksResult.parseFrom(it) }
                } catch (_: ZipException) {
                    raise(ApkSetParseError.InvalidFormat)
                } catch (_: IOException) {
                    raise(ApkSetParseError.IoError)
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
                        raise(ApkSetParseError.RequirementError.Missing64BitCode)
                    }
                }

                // Check compliance of individual APKs
                val apkPaths = buildApksResult.apkPaths()
                var pinnedSigningCert: X509Certificate? = null
                var pinnedVersionCode: Int? = null
                var firstEncounteredVersionName: String? = null
                var lowestEncounteredTargetSdk: Int? = null
                for (apkPath in apkPaths) {
                    val apkEntry = zipFile
                        .getEntry(apkPath)
                        ?: raise(ApkSetParseError.InvalidFormat)
                    try {
                        zipFile.getInputStream(apkEntry)
                    } catch (_: ZipException) {
                        raise(ApkSetParseError.InvalidFormat)
                    } catch (_: IOException) {
                        raise(ApkSetParseError.IoError)
                    }.use { apkInputStream ->
                        TempFile(tmpApkDir).use { tempApk ->
                            tempApk.path.outputStream().use { apkInputStream.copyTo(it) }

                            val apk = Apk.parse(tempApk.path).bind()

                            // Check that all APKs are signed with the same certificate to ensure
                            // that 1) we have an accurate view of how the app is signed and 2) that
                            // the app is installable
                            //
                            // https://developer.android.com/reference/android/content/pm/PackageInstaller
                            if (pinnedSigningCert == null) {
                                pinnedSigningCert = apk.signingCertificate
                            } else if (apk.signingCertificate != pinnedSigningCert) {
                                raise(ApkSetParseError.InvalidFormat)
                            }

                            // Check that the package name (application ID) of all APKs is the same
                            // to ensure the app is installable
                            //
                            // https://developer.android.com/reference/android/content/pm/PackageInstaller
                            if (apk.manifest.`package` != buildApksResult.packageName) {
                                raise(ApkSetParseError.InvalidFormat)
                            }

                            // Check that the version code of all APKs is the same to ensure the app
                            // is installable
                            //
                            // https://developer.android.com/reference/android/content/pm/PackageInstaller
                            if (pinnedVersionCode == null) {
                                pinnedVersionCode = apk.manifest.versionCode
                            } else if (apk.manifest.versionCode != pinnedVersionCode) {
                                raise(ApkSetParseError.InvalidFormat)
                            }

                            // Save lowest encountered target SDK for target SDK compliance checking
                            val apkTargetSdk = apk.manifest.usesSdk?.targetSdkVersion
                            val lowestTargetSdk = lowestEncounteredTargetSdk
                            if (
                                apkTargetSdk != null
                                && (lowestTargetSdk == null || apkTargetSdk < lowestTargetSdk)
                            ) {
                                lowestEncounteredTargetSdk = apkTargetSdk
                            }
                            // Save first encountered version name for informational purposes
                            if (
                                firstEncounteredVersionName == null
                                && apk.manifest.versionName != null
                            ) {
                                firstEncounteredVersionName = apk.manifest.versionName
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
                    raise(ApkSetParseError.InvalidFormat)
                }

                // Check compliance with target SDK requirement
                //
                // https://accrescent.app/docs/guide/appendix/requirements.html#target-sdk
                if (lowestEncounteredTargetSdk < MIN_TARGET_SDK) {
                    raise(ApkSetParseError.RequirementError.LowTargetSdk)
                }

                ApkSet(
                    applicationId = buildApksResult.packageName,
                    versionCode = pinnedVersionCode,
                    versionName = firstEncounteredVersionName,
                    targetSdk = lowestEncounteredTargetSdk,
                    signingCert = pinnedSigningCert,
                    buildApksResult = buildApksResult,
                )
            }
        }
    }
}
