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

public data class ApkSetMetadata(
    val appId: AppId,
    val versionCode: Int,
    val versionName: String,
    val targetSdk: Int,
    val bundletoolVersion: String,
    val reviewIssues: List<String>,
    val abiSplits: Set<String>,
    val densitySplits: Set<String>,
    val langSplits: Set<String>,
    val entrySplitNames: Map<String, Optional<String>>,
)

/**
 * The minimum acceptable bundletool version used to generate the APK set. This version is taken
 * from a recent Android Studio release.
 */
private val MIN_BUNDLETOOL_VERSION = Version.Builder("1.11.4").build()

/**
 * Parses an APK set into its metadata
 *
 * For now this function attempts to determine whether the APK set is valid on a best-effort
 * basis, so it may accept files which are not strictly valid APK sets. However, any APK set it
 * rejects is certainly invalid. It currently accepts the given file as a valid APK set according
 * to the following criteria:
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
 * - exactly one APK is a base APK (i.e., has an empty split name)
 * - the base APK specifies a version name
 *
 * @return metadata describing the APK set and the app it represents
 * @throws InvalidApkSetException the APK set is invalid
 */
public fun parseApkSet(file: InputStream): ApkSetMetadata {
    var appId: AppId? = null
    var versionCode: Int? = null
    var versionName: String? = null
    var targetSdk: Int? = null
    var bundletoolVersion: String? = null
    val reviewIssues = mutableListOf<String>()
    val splitNames = mutableSetOf<String?>()
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
                    throw InvalidApkSetException("bundletool metadata not valid")
                }
                // Validate bundletool version
                val parsedBundletoolVersion = try {
                    Version.Builder(bundletoolMetadata.bundletool.version).build()
                } catch (e: ParseException) {
                    throw InvalidApkSetException("invalid bundletool version")
                }
                if (parsedBundletoolVersion >= MIN_BUNDLETOOL_VERSION) {
                    bundletoolVersion = parsedBundletoolVersion.toString()
                } else {
                    throw InvalidApkSetException(
                        "APK set generated with bundletool $parsedBundletoolVersion" +
                            " but minimum supported version is $MIN_BUNDLETOOL_VERSION"
                    )
                }
                return@forEach
            }

            val apk = when (val result = Apk.parse(entryBytes)) {
                is ParseApkResult.Ok -> result.apk
                is ParseApkResult.Error -> throw InvalidApkSetException(result.message)
            }

            // Pin the APK signing certificates on the first APK encountered to ensure split APKs
            // can actually be installed.
            if (pinnedCertHashes.isEmpty()) {
                pinnedCertHashes = apk.signerCertificates.map { it.fingerprint() }
            } else {
                // Check against pinned certificates
                val theseCertHashes = apk.signerCertificates.map { it.fingerprint() }
                if (theseCertHashes != pinnedCertHashes) {
                    throw InvalidApkSetException("APK signing certificates don't match each other")
                }
            }

            if (!splitNames.add(apk.manifest.split)) {
                throw InvalidApkSetException("duplicate split names found")
            }

            if (apk.manifest.application.debuggable == true) {
                throw InvalidApkSetException("application is debuggable")
            }
            if (apk.manifest.application.testOnly == true) {
                throw InvalidApkSetException("application is test only")
            }

            // Pin common data on the first manifest parsed to ensure all split APKs have the same
            // app ID and version code.
            if (appId == null) {
                appId = apk.manifest.`package`
                versionCode = apk.manifest.versionCode
            } else {
                // Check that the app ID and version code are the same as previously pinned
                if (apk.manifest.`package` != appId || apk.manifest.versionCode != versionCode) {
                    throw InvalidApkSetException("APK manifest info is not consistent across all APKs")
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
                apk.manifest.versionName?.let { versionName = it }
                    ?: throw InvalidApkSetException("base APK doesn't specify a version name")

                // Target SDK
                apk.manifest.usesSdk?.let { targetSdk = it.targetSdkVersion ?: it.minSdkVersion }
                    ?: throw InvalidApkSetException("base APK doesn't specify a target SDK")
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

        for (splitName in splitNames) {
            splitName?.let {
                try {
                    when (getSplitTypeForName(splitName)) {
                        SplitType.ABI -> abiSplits
                        SplitType.LANGUAGE -> langSplits
                        SplitType.SCREEN_DENSITY -> densitySplits
                    }.add(splitName.substringAfter("config."))
                } catch (e: SplitNameNotConfigException) {
                    throw InvalidApkSetException(e.message!!)
                }
            }
        }

        Triple(abiSplits.toSet(), langSplits.toSet(), densitySplits.toSet())
    }

    // If there isn't a base APK, freak out
    if (!splitNames.contains(null)) {
        throw InvalidApkSetException("no base APK found")
    }

    when {
        appId == null || versionCode == null -> throw InvalidApkSetException("no APKs found")
        versionName == null -> throw InvalidApkSetException("no version name specified")
        targetSdk == null -> throw InvalidApkSetException("no targetSdk specified")
        bundletoolVersion == null -> throw InvalidApkSetException("no bundletool version found")
    }

    return ApkSetMetadata(
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

public class InvalidApkSetException(message: String) : Exception(message)

public class SplitNameNotConfigException(splitName: String) :
    Exception("split name $splitName is not a config split name")
