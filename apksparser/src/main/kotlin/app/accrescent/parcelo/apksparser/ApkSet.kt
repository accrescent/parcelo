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
    var bundletoolVersion: String? = null
    var metadata: ApkSetMetadata? = null
    var pinnedCertHashes = emptyList<String>()
    val splitNames = mutableSetOf<String?>()

    ZipInputStream(file).use { zip ->
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

            // Pin the app metadata on the first manifest parsed to ensure all split APKs have the
            // same app ID and version code.
            if (metadata == null) {
                metadata =
                    ApkSetMetadata(
                        apk.manifest.`package`,
                        apk.manifest.versionCode,
                        "",
                        0,
                        "",
                        emptyList(),
                        emptySet(),
                        emptySet(),
                        emptySet(),
                        emptyMap(),
                    )
            } else {
                // Check that the metadata is the same as that previously pinned (sans the version
                // name for reasons described above).
                //
                // We can non-null assert the metadata here since the changing closure is called
                // sequentially.
                if (
                    apk.manifest.`package` != metadata!!.appId ||
                    apk.manifest.versionCode != metadata!!.versionCode
                ) {
                    throw InvalidApkSetException("APK manifest info is not consistent across all APKs")
                }
            }

            // Update the review issues, version name, and target SDK if this is the base APK
            if (apk.manifest.split == null) {
                // Permissions
                apk.manifest.usesPermissions?.let { permissions ->
                    metadata = metadata!!.copy(reviewIssues = permissions.map { it.name })
                }
                // Service intent filter actions
                apk.manifest.application.services?.let { services ->
                    val issues = metadata!!.reviewIssues.toMutableSet()
                    services
                        .flatMap { it.intentFilters ?: emptyList() }
                        .flatMap { it.actions }
                        .map { it.name }
                        .forEach { issues.add(it) }
                    metadata = metadata!!.copy(reviewIssues = issues.toList())
                }

                // Version name
                if (apk.manifest.versionName != null) {
                    metadata = metadata!!.copy(versionName = apk.manifest.versionName)
                } else {
                    throw InvalidApkSetException("base APK doesn't specify a version name")
                }

                // Target SDK
                if (apk.manifest.usesSdk != null) {
                    metadata = metadata!!.copy(
                        targetSdk = apk.manifest.usesSdk.targetSdkVersion
                            ?: apk.manifest.usesSdk.minSdkVersion
                    )
                } else {
                    throw InvalidApkSetException("base APK doesn't specify a target SDK")
                }
            }

            // Update the entry name -> split mapping
            val map = metadata!!.entrySplitNames.toMutableMap()
            map[entry.name] = if (apk.manifest.split == null) {
                Optional.empty()
            } else {
                Optional.of(apk.manifest.split.substringAfter("config."))
            }
            metadata = metadata!!.copy(entrySplitNames = map)
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
    metadata = metadata?.copy(
        abiSplits = abiSplits,
        langSplits = langSplits,
        densitySplits = densitySplits
    )

    if (bundletoolVersion != null) {
        metadata = metadata?.copy(bundletoolVersion = bundletoolVersion!!)
    } else {
        throw InvalidApkSetException("no bundletool version found")
    }

    // If there isn't a base APK, freak out
    if (!splitNames.contains(null)) {
        throw InvalidApkSetException("no base APK found")
    }

    return metadata ?: throw InvalidApkSetException("no APKs found")
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
