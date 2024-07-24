// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.apksparser

import com.android.bundle.Commands.BuildApksResult
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.zip.ZipException
import java.util.zip.ZipFile

private const val TOC_PATH = "toc.pb"

public class ApkSet private constructor(
    public val versionCode: Int,
    public val versionName: String,
    public val targetSdk: Int,
    public val metadata: BuildApksResult,
    public val reviewIssues: Set<String>,
) {
    public companion object {
        /**
         * Parses an APK set into its metadata
         *
         * Only a subset of all valid APK sets can be successfully parsed by this function, as it
         * imposes some additional constraints and makes some additional security checks which are
         * not strictly necessary, but are helpful for our purposes. It currently accepts the given
         * file as a valid APK set according to the following criteria:
         *
         * - the input file is a valid ZIP
         * - the entry with name "toc.pb" is a valid BuildApksResult protocol buffer
         * - the input ZIP contains at least one APK
         * - all APKs have the same signing certificates
         * - all APKs have the same app ID and version code
         * - the app ID of the APKs matches the package name of the BuildApksResult
         * - all APKs must not be debuggable
         * - all APKs must not be marked test only
         *
         * @return metadata describing the APK set and the app it represents
         */
        public fun parse(file: File): ParseApkSetResult {
            try {
                ZipFile(file)
            } catch (_: ZipException) {
                return ParseApkSetResult.Error.ZipFormatError
            } catch (e: IOException) {
                return ParseApkSetResult.Error.IoError(e)
            }.use { zip ->
                val tocEntry = zip.getEntry(TOC_PATH) ?: return ParseApkSetResult.Error.TocNotFound
                val buildApksResult = try {
                    zip.getInputStream(tocEntry).use { entryStream ->
                        try {
                            BuildApksResult.newBuilder().mergeFrom(entryStream).build()
                        } catch (e: IOException) {
                            return ParseApkSetResult.Error.IoError(e)
                        }
                    }
                } catch (_: ZipException) {
                    return ParseApkSetResult.Error.ZipFormatError
                } catch (e: IOException) {
                    return ParseApkSetResult.Error.IoError(e)
                }

                var versionCode: Int? = null
                var pinnedCertHashes = emptyList<String>()
                val reviewIssues = mutableSetOf<String>()
                var versionName: String? = null
                var targetSdk: Int? = null

                buildApksResult.variantList.forEach { variant ->
                    variant.apkSetList.forEach { apkSet ->
                        apkSet.apkDescriptionList.forEach { apkDescription ->
                            val apkPath = apkDescription.path
                                ?: return ParseApkSetResult.Error.MissingPathError
                            // Fail if a missing APK is a split APK.
                            //
                            // Because bundletool unconditionally generates standalone APKs for apps
                            // with a low enough minSdk, some developers are forced to use a tool
                            // such as apkstripper (https://github.com/lberrymage/apkstripper) to
                            // remove them so their APK set fits under Parcelo's size limit.
                            // However, apkstripper only naively removes standalone APKs from the
                            // APK set without updating its table of contents, which means that if
                            // we require all APKs listed in the table of contents to be present,
                            // developers using apkstripper will be unable to upload their app.
                            //
                            // Parcelo makes no use of APK types besides split APKs, so we can
                            // safely ignore missing APKs of any other type.
                            val apkEntry = zip.getEntry(apkPath)
                                ?: if (apkDescription.hasSplitApkMetadata()) {
                                    return ParseApkSetResult.Error.MissingApkError(apkPath)
                                } else {
                                    return@forEach
                                }
                            val apkBytes = try {
                                zip.getInputStream(apkEntry).use { it.readBytes() }
                            } catch (_: ZipException) {
                                return ParseApkSetResult.Error.ZipFormatError
                            } catch (e: IOException) {
                                return ParseApkSetResult.Error.IoError(e)
                            }
                            val apk = when (val result = Apk.parse(apkBytes)) {
                                is ParseApkResult.Ok -> result.apk
                                is ParseApkResult.Error -> return ParseApkSetResult.Error.ApkParseError(
                                    result
                                )
                            }

                            // Verify that all APKs have the same cert hashes
                            if (pinnedCertHashes.isEmpty()) {
                                pinnedCertHashes = apk.signerCertificates.map { it.fingerprint() }
                            } else {
                                // Check against pinned certificates
                                val theseCertHashes =
                                    apk.signerCertificates.map { it.fingerprint() }
                                if (theseCertHashes != pinnedCertHashes) {
                                    return ParseApkSetResult.Error.SigningCertMismatchError
                                }
                            }

                            // Verify that all APKs have the same package name as the metadata
                            if (apk.manifest.`package`.value != buildApksResult.packageName) {
                                return ParseApkSetResult.Error.MismatchedAppIdError(
                                    buildApksResult.packageName,
                                    apk.manifest.`package`.value,
                                )
                            }

                            // Verify that all APKs have the same version code
                            if (versionCode == null) {
                                versionCode = apk.manifest.versionCode
                            } else {
                                if (apk.manifest.versionCode != versionCode) {
                                    return ParseApkSetResult.Error.MismatchedVersionCodeError(
                                        versionCode,
                                        apk.manifest.versionCode,
                                    )
                                }
                            }

                            // Verify the apk is not debuggable
                            // https://accrescent.app/docs/guide/appendix/requirements.html#androiddebuggable
                            if (apk.manifest.application.debuggable == true) {
                                return ParseApkSetResult.Error.DebuggableError
                            }

                            // Verify the apk is not test-only
                            // https://accrescent.app/docs/guide/appendix/requirements.html#androidtestonly
                            if (apk.manifest.application.testOnly == true) {
                                return ParseApkSetResult.Error.TestOnlyError
                            }

                            // Update the review issues, version name, and target SDK if present

                            // Permissions
                            apk.manifest.usesPermissions?.let { permissions ->
                                reviewIssues.addAll(permissions.map { it.name })
                            }

                            // Service intent filter actions
                            apk.manifest.application.services?.let { services ->
                                services
                                    .flatMap { it.intentFilters.orEmpty() }
                                    .flatMap { it.actions }
                                    .map { it.name }
                                    .forEach { reviewIssues.add(it) }
                            }

                            // Version name
                            apk.manifest.versionName?.let { versionName = it }

                            // Target SDK
                            apk.manifest.usesSdk?.let {
                                targetSdk = it.targetSdkVersion ?: it.minSdkVersion
                            }
                        }
                    }
                }

                return ParseApkSetResult.Ok(
                    ApkSet(
                        versionCode = versionCode
                            ?: return ParseApkSetResult.Error.MissingVersionCodeError,
                        versionName = versionName
                            ?: return ParseApkSetResult.Error.VersionNameNotFoundError,
                        targetSdk = targetSdk
                            ?: return ParseApkSetResult.Error.VersionNameNotFoundError,
                        reviewIssues = reviewIssues,
                        metadata = buildApksResult,
                    )
                )
            }
        }
    }
}

/**
 * Representation of the result of attempting to parse an APK set
 */
public sealed class ParseApkSetResult {
    /**
     * The result of successful parsing
     */
    public data class Ok(val apkSet: ApkSet) : ParseApkSetResult()

    /**
     * The result of failed parsing
     */
    public sealed class Error : ParseApkSetResult() {
        /**
         * A message describing the error
         */
        public abstract val message: String

        /**
         * An error was encountered in parsing an APK
         */
        public data class ApkParseError(val error: ParseApkResult.Error) : Error() {
            override val message: String = error.message
        }

        /**
         * A mismatch exists between APK signing certificates
         */
        public data object SigningCertMismatchError : Error() {
            override val message: String = "APK signing certificates don't match each other"
        }

        /**
         * Application is debuggable
         */
        public data object DebuggableError : Error() {
            override val message: String = "application is debuggable"
        }

        /**
         * Application is test-only
         */
        public data object TestOnlyError : Error() {
            override val message: String = "application is test only"
        }

        /**
         * The app ID of an APK does not match the app ID in the metadata
         */
        public data class MismatchedAppIdError(val expected: String, val found: String) : Error() {
            override val message: String = "expected app ID $expected, found $found"
        }

        /**
         * The version codes across all APKs are not consistent
         */
        public data class MismatchedVersionCodeError(val pinned: Int, val other: Int) : Error() {
            override val message: String =
                "encountered version code $other doesn't match pinned version $pinned"
        }

        /**
         * No version name was found in the APK set
         */
        public data object VersionNameNotFoundError : Error() {
            override val message: String = "no version name specified"
        }

        /**
         * No target SDK was found in the APK set
         */
        public data object TargetSdkNotFoundError : Error() {
            override val message: String = "no targetSdk specified"
        }

        /**
         * An I/O error occurred
         */
        public data class IoError(val e: IOException) : Error() {
            override val message: String = "I/O error: ${e.message}"
        }

        /**
         * The table of contents metadata was not found
         */
        public data object TocNotFound : Error() {
            override val message: String = "table of contents not found"
        }

        /**
         * APK was missing a required path field
         */
        public data object MissingPathError : Error() {
            override val message: String = "missing required APK path field"
        }

        /**
         * The APK at the supplied path was not found
         */
        public data class MissingApkError(val path: String) : Error() {
            override val message: String = "missing APK at path $path"
        }

        /**
         * The provided file is not a valid ZIP
         */
        public data object ZipFormatError : Error() {
            override val message: String = "file is not a valid ZIP"
        }

        /**
         * The application is missing a version code
         */
        public data object MissingVersionCodeError : Error() {
            override val message: String = "no version code found"
        }
    }
}

/**
 * Gets the certificate's SHA256 fingerprint
 */
private fun X509Certificate.fingerprint(): String {
    return MessageDigest
        .getInstance("SHA-256")
        .digest(this.encoded)
        .joinToString("") { "%02x".format(it) }
}
