// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.apksparser

import com.android.bundle.Commands.BuildApksResult
import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Optional
import java.util.zip.ZipInputStream

public class ApkSet private constructor(
    public val appId: AppId,
    public val versionCode: Int,
    public val versionName: String,
    public val targetSdk: Int,
    public val bundletoolVersion: Version,
    public val reviewIssues: List<String>,
    public val abiSplits: Set<String>,
    public val densitySplits: Set<String>,
    public val langSplits: Set<String>,
    public val entrySplitNames: Map<String, Optional<String>>,
) {
    private data class Split(val name: String?, val variantNumber: Int)

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
         * - all non-directory entries in said ZIP except for "toc.pb" are valid APKs
         * - "toc.pb" is a valid BuildApksResult protocol buffer
         * - the input ZIP contains at least one APK
         * - all APKs must not be debuggable
         * - all APKs must not be marked test only
         * - all APKs have the same signing certificates
         * - all APKs have the same app ID and version code
         * - all APKs have unique split names
         * - at least one APK is a base APK (i.e., has an empty split name)
         * - the base APKs specify a version name
         *
         * @return metadata describing the APK set and the app it represents
         */
        public fun parse(file: InputStream): ParseApkSetResult {
            var appId: AppId? = null
            var versionCode: Int? = null
            var versionName: String? = null
            var targetSdk: Int? = null
            var bundletoolVersion: Version? = null
            val pathToVariantMap = mutableMapOf<String, Int>()
            val reviewIssues = mutableListOf<String>()
            val splits = mutableSetOf<Split>()
            val entrySplitNames = mutableMapOf<String, Optional<String>>()

            ZipInputStream(file).use { zip ->
                var pinnedCertHashes = emptyList<String>()

                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    val entryBytes = zip.readBytes()

                    // Parse metadata
                    if (entry.name == "toc.pb") {
                        val bundletoolMetadata = try {
                            BuildApksResult.newBuilder().mergeFrom(entryBytes).build()
                        } catch (e: InvalidProtocolBufferException) {
                            return ParseApkSetResult.Error.BundletoolMetadataError
                        }

                        // Update path to variant number mapping. Since we rely on this for APK
                        // validation, this places an implicit requirement on the APK set that
                        // toc.pb must be the first entry.
                        bundletoolMetadata.variantList.forEach { variant ->
                            variant.apkSetList.forEach { apkSet ->
                                apkSet.apkDescriptionList.forEach { apkDescription ->
                                    pathToVariantMap[apkDescription.path] = variant.variantNumber
                                }
                            }
                        }

                        // Validate bundletool version
                        bundletoolVersion = try {
                            Version.Builder(bundletoolMetadata.bundletool.version).build()
                        } catch (e: ParseException) {
                            return ParseApkSetResult.Error.BundletoolVersionError
                        }
                        return@forEach
                    }

                    val apk = when (val result = Apk.parse(entryBytes)) {
                        is ParseApkResult.Ok -> result.apk
                        is ParseApkResult.Error -> return ParseApkSetResult.Error.ApkParseError(
                            result
                        )
                    }

                    // Pin the APK signing certificates on the first APK encountered to ensure split APKs
                    // can actually be installed.
                    if (pinnedCertHashes.isEmpty()) {
                        pinnedCertHashes = apk.signerCertificates.map { it.fingerprint() }
                    } else {
                        // Check against pinned certificates
                        val theseCertHashes = apk.signerCertificates.map { it.fingerprint() }
                        if (theseCertHashes != pinnedCertHashes) {
                            return ParseApkSetResult.Error.SigningCertMismatchError
                        }
                    }

                    val variantNumber = pathToVariantMap[entry.name]
                        ?: return ParseApkSetResult.Error.VariantNumberNotFoundError(entry.name)
                    if (!splits.add(Split(apk.manifest.split, variantNumber))) {
                        return ParseApkSetResult.Error.DuplicateSplitError
                    }

                    if (apk.manifest.application.debuggable == true) {
                        return ParseApkSetResult.Error.DebuggableError
                    }
                    if (apk.manifest.application.testOnly == true) {
                        return ParseApkSetResult.Error.TestOnlyError
                    }

                    // Pin common data on the first manifest parsed to ensure all split APKs have
                    // the same app ID and version code.
                    if (appId == null) {
                        appId = apk.manifest.`package`
                        versionCode = apk.manifest.versionCode
                    } else {
                        // Check that the app ID and version code are the same as previously pinned
                        if (
                            apk.manifest.`package` != appId ||
                            apk.manifest.versionCode != versionCode
                        ) {
                            return ParseApkSetResult.Error.ManifestInfoInconsistentError
                        }
                    }

                    // Update the review issues, version name, and target SDK if this is the base APK
                    if (apk.manifest.split == null) {
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
                        apk.manifest.versionName
                            ?.let { versionName = it }
                            ?: return ParseApkSetResult.Error.BaseApkVersionNameUnspecifiedError

                        // Target SDK
                        apk.manifest.usesSdk
                            ?.let { targetSdk = it.targetSdkVersion ?: it.minSdkVersion }
                            ?: return ParseApkSetResult.Error.BaseApkTargetSdkUnspecifiedError
                    }

                    // Update the entry name -> split mapping
                    entrySplitNames[entry.name] = apk.manifest.split
                        ?.let { Optional.of(it.substringAfter("config.")) }
                        ?: Optional.empty()
                }
            }

            // Update metadata with split config names
            val (abiSplits, langSplits, densitySplits) = run {
                val abiSplits = mutableSetOf<String>()
                val langSplits = mutableSetOf<String>()
                val densitySplits = mutableSetOf<String>()

                for (splitName in splits.map { it.name }) {
                    splitName?.let {
                        try {
                            when (getSplitTypeForName(splitName)) {
                                SplitType.ABI -> abiSplits
                                SplitType.LANGUAGE -> langSplits
                                SplitType.SCREEN_DENSITY -> densitySplits
                            }.add(splitName.substringAfter("config."))
                        } catch (e: SplitNameNotConfigException) {
                            return ParseApkSetResult.Error.InvalidSplitNameError(splitName)
                        }
                    }
                }

                Triple(abiSplits.toSet(), langSplits.toSet(), densitySplits.toSet())
            }

            // If there isn't a base APK, freak out
            if (splits.none { it.name == null}) {
                return ParseApkSetResult.Error.BaseApkNotFoundError
            }

            when {
                appId == null || versionCode == null -> return ParseApkSetResult.Error.ApksNotFoundError
                versionName == null -> return ParseApkSetResult.Error.VersionNameNotFoundError
                targetSdk == null -> return ParseApkSetResult.Error.TargetSdkNotFoundError
                bundletoolVersion == null -> return ParseApkSetResult.Error.BundletoolVersionNotFoundError
            }

            val apkSet = ApkSet(
                appId!!,
                versionCode!!,
                versionName!!,
                targetSdk!!,
                bundletoolVersion!!,
                reviewIssues,
                abiSplits,
                densitySplits,
                langSplits,
                entrySplitNames,
            )

            return ParseApkSetResult.Ok(apkSet)
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
         * An error was encountered in parsing the bundletool metadata
         */
        public object BundletoolMetadataError : Error() {
            override val message: String = "bundletool metadata not valid"
        }

        /**
         * An error was encountered in parsing the bundletool version
         */
        public object BundletoolVersionError : Error() {
            override val message: String = "invalid bundletool version"
        }

        /**
         * A mismatch exists between APK signing certificates
         */
        public object SigningCertMismatchError : Error() {
            override val message: String = "APK signing certificates don't match each other"
        }

        /**
         * A duplicate split APK name was detected
         */
        public object DuplicateSplitError : Error() {
            override val message: String = "duplicate split names found"
        }

        /**
         * Application is debuggable
         */
        public object DebuggableError : Error() {
            override val message: String = "application is debuggable"
        }

        /**
         * Application is test-only
         */
        public object TestOnlyError : Error() {
            override val message: String = "application is test only"
        }

        /**
         * Android manifest info (app ID and version name) isn't the same across all APKs
         */
        public object ManifestInfoInconsistentError : Error() {
            override val message: String = "APK manifest info is not consistent across all APKs"
        }

        /**
         * The base APK doesn't specify a version name
         */
        public object BaseApkVersionNameUnspecifiedError : Error() {
            override val message: String = "base APK doesn't specify a version name"
        }

        /**
         * The base APK doesn't specify a target SDK
         */
        public object BaseApkTargetSdkUnspecifiedError : Error() {
            override val message: String = "base APK doesn't specify a target SDK"
        }

        /**
         * No base APK was found
         */
        public object BaseApkNotFoundError : Error() {
            override val message: String = "no base APK found"
        }

        /**
         * No APKs were found
         */
        public object ApksNotFoundError : Error() {
            override val message: String = "no APKs found"
        }

        /**
         * No version name was found in the APK set
         */
        public object VersionNameNotFoundError : Error() {
            override val message: String = "no version name specified"
        }

        /**
         * No target SDK was found in the APK set
         */
        public object TargetSdkNotFoundError : Error() {
            override val message: String = "no targetSdk specified"
        }

        /**
         * No bundletool version was found in the APK set
         */
        public object BundletoolVersionNotFoundError : Error() {
            override val message: String = "no bundletool version found"
        }

        /**
         * Parsing encountered an invalid configuration split APK name
         */
        public data class InvalidSplitNameError(val splitName: String) : Error() {
            override val message: String = "$splitName is not a valid configuration split name"
        }

        /**
         * No variant number was found for the APK at the given path
         */
        public data class VariantNumberNotFoundError(val path: String) : Error() {
            override val message: String = "no variant number found for $path"
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

public enum class SplitType { ABI, LANGUAGE, SCREEN_DENSITY }

private val abiSplitNames = setOf("arm64_v8a", "armeabi_v7a", "x86", "x86_64")
private val densitySplitNames =
    setOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "nodpi", "tvdpi")

/**
 * Detects the configuration split API type based on its name
 *
 * @throws SplitNameNotConfigException the split name is not a valid configuration split name
 */
private fun getSplitTypeForName(splitName: String): SplitType {
    val configName = splitName.substringAfter("config.")
    if (configName == splitName) {
        throw SplitNameNotConfigException(splitName)
    }

    return if (abiSplitNames.contains(configName)) {
        SplitType.ABI
    } else if (densitySplitNames.contains(configName)) {
        SplitType.SCREEN_DENSITY
    } else {
        SplitType.LANGUAGE
    }
}

private class SplitNameNotConfigException(splitName: String) :
    Exception("split name $splitName is not a config split name")
