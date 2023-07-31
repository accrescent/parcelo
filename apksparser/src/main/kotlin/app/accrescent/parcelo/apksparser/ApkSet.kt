package app.accrescent.parcelo.apksparser

import com.android.bundle.Commands
import com.android.bundle.Commands.BuildApksResult
import com.android.bundle.Targeting
import com.android.bundle.Targeting.Abi.AbiAlias
import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.zip.ZipInputStream

public class ApkSet private constructor(
    public val appId: String,
    public val versionCode: Int,
    public val versionName: String,
    public val minSdk: Int,
    public val targetSdk: Int,
    public val bundletoolVersion: Version,
    public val reviewIssues: List<String>,
    public val variants: Set<Variant>
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
         * - all non-directory entries in said ZIP except for "toc.pb" are valid APKs
         * - "toc.pb" is a valid BuildApksResult protocol buffer (referred to as the metadata)
         * - the input ZIP contains at least one APK
         * - all APKs must not be debuggable
         * - all APKs must not be marked test only
         * - all APKs have the same signing certificates
         * - all APKs have the same app ID and version code
         * - all APKs have unique split names
         * - the metadata is not marked as in testing mode
         * - the metadata specifies at least one variant (group of APKs)
         * - the metadata does not use any targeting functionality outside SDK for variants, and conventional
         * abi/lang/density for APKs.
         * - the metadata does not use any module functionality
         * - the metadata only uses non-instant split APKs
         * - there are no duplicate variants, apks, splits, etc.
         * - each apk specified has a valid corresponding APK file of the same split id in the apk set
         * - each variant has a base APK
         * - the base APKs and metadata collectively specify consistent application ids, version codes, version names,
         * sdk versions, etc.
         *
         * @return metadata describing the APK set and the app it represents
         */
        public fun parse(file: InputStream): ParseApkSetResult {
            data class ApkEntry(val innerApk: Apk, val path: String)

            var bundletoolMetadata: BuildApksResult? = null
            val apkEntries = mutableListOf<ApkEntry>()

            ZipInputStream(file).use { zip ->
                var pinnedCertHashes = emptyList<String>()

                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    val entryBytes = zip.readBytes()

                    if (entry.name == "toc.pb") {
                        bundletoolMetadata = try {
                            BuildApksResult.newBuilder().mergeFrom(entryBytes).build()
                        } catch (e: InvalidProtocolBufferException) {
                            return ParseApkSetResult.Error.MetadataParseError
                        }
                    }

                    val apk = when (val result = Apk.parse(entryBytes)) {
                        is ParseApkResult.Ok -> result.apk
                        is ParseApkResult.Error -> return ParseApkSetResult.Error.ApkParseError(result)
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

                    // Fail on debuggable and testing APKs which have naturally weaker signatures
                    if (apk.manifest.application.debuggable == true) {
                        return ParseApkSetResult.Error.DebuggableError
                    }

                    if (apk.manifest.application.testOnly == true) {
                        return ParseApkSetResult.Error.TestOnlyError
                    }

                    // Check that the app ID and version code are the same as previously pinned
                    apkEntries.lastOrNull()?.run {
                        if (innerApk.manifest.`package` != apk.manifest.`package` ||
                            innerApk.manifest.versionCode != apk.manifest.versionCode
                        ) {
                            return ParseApkSetResult.Error.ManifestInfoInconsistentError
                        }
                    }
                }
            }

            // Make sure that we successfully found some metadata and a non-zero amount of entries
            val metadata = bundletoolMetadata ?: return ParseApkSetResult.Error.MetadataNotFoundError
            if (apkEntries.isEmpty()) return ParseApkSetResult.Error.ApksNotFoundError

            val bundletoolVersion = try {
                Version.Builder(metadata.bundletool.version).build()
            } catch (e: ParseException) {
                return ParseApkSetResult.Error.BundletoolVersionError
            }

            // It's somewhat unclear, but this flag is also likely related to testing and should be treated as such.
            if (metadata.localTestingInfo.enabled) {
                return ParseApkSetResult.Error.TestOnlyError
            }

            // Check for unsupported base-level metadata features.
            if (metadata.assetSliceSetList.isNotEmpty() ||
                metadata.assetModulesInfo != null ||
                metadata.defaultTargetingValueList.isNotEmpty() ||
                metadata.permanentlyFusedModulesList.isNotEmpty()
            ) {
                return ParseApkSetResult.Error.MetadataUnsupportedError
            }

            // If we don't have any variants, we don't have any APKs.
            if (metadata.variantList.isEmpty()) {
                return ParseApkSetResult.Error.VariantsNotFoundError
            }

            // Track variants alongside their SDK numbers and numberings to mitigate duplicate entries
            val variants = mutableSetOf<Variant>()
            val variantSdks = mutableSetOf<Int>()
            val variantNumbering = mutableSetOf<Int>()

            // We will be encountering base splits as we parse variants, so start tracking the information
            // we need from them. Since there can be multiple, we want to make sure we are tracking "aggregate"
            // information, so we can check for inconsistency later.
            val appIds = mutableSetOf<String>()
            val versionCodes = mutableSetOf<Int>()
            val versionNames = mutableSetOf<String?>()
            val usesSdks = mutableSetOf<UsesSdk?>()
            val reviewIssueSets = mutableSetOf<List<String>>()

            val consumed = mutableSetOf<ApkEntry>()

            for (variant in metadata.variantList) {
                val variantTargeting = variant.targeting

                // Currently we only accept variant targeting by SDK, as that is the only reasonable way
                // the base splits (which usually have version-dependent compression) can be propertly tracked.
                if (variantTargeting.abiTargeting != null ||
                    variantTargeting.screenDensityTargeting != null ||
                    variantTargeting.multiAbiTargeting != null ||
                    variantTargeting.textureCompressionFormatTargeting != null ||
                    variantTargeting.sdkRuntimeTargeting != null
                ) {
                    return ParseApkSetResult.Error.VariantTargetingUnsupportedError
                }

                // Since we've ruled out all other targets as being null, if this field is missing too, that means
                // that the variant has no target information usable by the device, which is probably invalid.
                val sdkTargeting = variant.targeting.sdkVersionTargeting
                    ?: return ParseApkSetResult.Error.VariantTargetsNothingError
                // Only accept a single SDK value for now. You will see this in a few other places as it's currently
                // unclear why or how these fields would have more than one value.
                val sdk = sdkTargeting.valueList.singleOrNull()
                    ?: return ParseApkSetResult.Error.VariantSDKUnsupportedError
                val minSdk = sdk.min.value

                // Mitigate duplicate variants
                if (!variantSdks.add(minSdk) || !variantNumbering.add(variant.variantNumber)) {
                    return ParseApkSetResult.Error.DuplicateVariantError
                }

                val apkSet = variant.apkSetList.singleOrNull() ?:
                    return ParseApkSetResult.Error.ApkSetUnsupportedError

                // We currently don't support non-base feature modules. Error out if any other modules are encountered
                // to avoid putting invalid APKs into the repository.
                val module = apkSet.moduleMetadata
                if (module.name != "base" || module.moduleType != Commands.FeatureModuleType.FEATURE_MODULE ||
                    module.isInstant || module.dependenciesList.isNotEmpty() ||
                    module.runtimeEnabledSdkDependenciesList.isNotEmpty()
                ) {
                    return ParseApkSetResult.Error.ModuleUnsupportedError
                }

                // Base feature modules have no targeting, so targeted modules are also considered a sign of several
                // feature modules and considered invalid.
                val moduleTargeting = apkSet.moduleMetadata.targeting
                if (moduleTargeting.sdkVersionTargeting != null || moduleTargeting.deviceFeatureTargetingList.isNotEmpty() ||
                    moduleTargeting.userCountriesTargeting != null || moduleTargeting.deviceGroupTargeting != null
                ) {
                    return ParseApkSetResult.Error.ModuleTargetingUnsupportedError
                }

                // There should only be one base split, and then an arbitrary amount of splits for ABI, language, and
                // density. Note that splits in several variants could point to the same APK file.
                var baseSplit: String? = null
                val abiSplits = mutableMapOf<String, String>()
                val langSplits = mutableMapOf<String, String>()
                val densitySplits = mutableMapOf<String, String>()
                val apkPaths = mutableSetOf<String>()
                for (apkDescription in apkSet.apkDescriptionList) {
                    // Disallow any non-split (i.e. instant, APEX) APKs. Also disallow any rotated keys for now
                    // until it's clear what they do. TODO: See prior
                    if (apkDescription.splitApkMetadata == null ||
                        !apkDescription.signingDescription.signedWithRotatedKey
                    ) {
                        return ParseApkSetResult.Error.ApkDescriptionUnsupportedError
                    }

                    // Each APK description in a Variant should point to a unique APK file in the set. Note that APK
                    // descriptions across different variants can point to the same APK file.
                    if (!apkPaths.add(apkDescription.path)) {
                        return ParseApkSetResult.Error.DuplicateSplitPathsError
                    }

                    val path = apkDescription.path
                    val splitId = apkDescription.splitApkMetadata.splitId
                    val isBase = apkDescription.splitApkMetadata.isMasterSplit
                    if (isBase || splitId.isEmpty()) {
                        // Base APKs should have the corresponding metadata flag set and an empty ID. Any other
                        // configuration should be invalid.
                        if (!isBase || splitId.isNotEmpty()) {
                            return ParseApkSetResult.Error.BaseApkDescriptionInconsistentError
                        }

                        // There can only be one base apk description in a variant.
                        if (baseSplit != null) {
                            return ParseApkSetResult.Error.MultipleBaseApkDescriptionsError
                        }

                        // A base APK file should have a null split instead of an empty one, search for such.
                        val apkEntry = apkEntries.filter { it.path == path && it.innerApk.manifest.split == null }
                            .ifEmpty { return ParseApkSetResult.Error.NoBaseSplitsFoundError }
                            .singleOrNull() ?: return ParseApkSetResult.Error.MultipleBaseSplitsFoundError

                        baseSplit = apkEntry.path

                        // Aggregate the apk's manifest values
                        val manifest = apkEntry.innerApk.manifest
                        appIds.add(manifest.`package`.value)
                        versionCodes.add(manifest.versionCode)
                        versionNames.add(manifest.versionName)
                        usesSdks.add(manifest.usesSdk)

                        // Aggregate any permissions/service values that may need to be reviewed
                        val issues = mutableListOf<String>()
                        manifest.usesPermissions?.let { permissions ->
                            permissions.mapTo(issues) { it.name }
                        }
                        manifest.application.services?.let { services ->
                            services
                                .flatMap { service -> service.intentFilters.orEmpty().flatMap { it.actions } }
                                .mapTo(issues) { it.name }
                        }
                        reviewIssueSets.add(issues)

                        consumed.add(apkEntry)
                    } else {
                        // These APK targets are not supported by the client and should be disallowed for now.
                        val apkTargeting = apkDescription.targeting
                        if (apkTargeting.textureCompressionFormatTargeting != null ||
                            apkTargeting.multiAbiTargeting != null ||
                            apkTargeting.sanitizerTargeting != null ||
                            apkTargeting.deviceTierTargeting != null ||
                            apkTargeting.countrySetTargeting != null
                        ) {
                            return ParseApkSetResult.Error.ApkTargetingUnsupportedError
                        }

                        // We only allow an APK to target either abi, language, or density, currently. Combinations
                        // are disallowed.
                        val abiTargeting = apkTargeting.abiTargeting
                        val langTargeting = apkTargeting.languageTargeting
                        val densityTargeting = apkTargeting.screenDensityTargeting

                        val (id, dst) = when {
                            abiTargeting != null -> {
                                if (langTargeting != null || densityTargeting != null) {
                                    return ParseApkSetResult.Error.ApkTargetingUnsupportedError
                                }

                                // Transform the ABI alias into a string value usable with the repodata format
                                val abi = abiTargeting.valueList.singleOrNull()
                                    ?: return ParseApkSetResult.Error.ApkSetUnsupportedError
                                val name = when (abi.alias) {
                                    AbiAlias.ARMEABI_V7A -> "armeabi_v7a"
                                    AbiAlias.ARM64_V8A -> "arm64_v8a"
                                    AbiAlias.X86 -> "x86"
                                    AbiAlias.X86_64 -> "x86_64"
                                    else -> return ParseApkSetResult.Error.AbiUnsupportedError
                                }
                                Pair(name, abiSplits)
                            }

                            // Due to when condition logic, we actually don't need to fully check that the other
                            // two values are null since they were already ruled out by prior when blocks.

                            langTargeting != null -> {
                                if (densityTargeting != null) {
                                    return ParseApkSetResult.Error.ApkTargetingUnsupportedError
                                }

                                val lang = langTargeting.valueList.singleOrNull()
                                    ?: return ParseApkSetResult.Error.LangUnsupportedError
                                Pair(lang, langSplits)
                            }

                            densityTargeting != null -> {
                                // Transform the ABI alias into a string value usable with the repodata format
                                val density = densityTargeting.valueList.singleOrNull()
                                    ?: return ParseApkSetResult.Error.ApkSetUnsupportedError
                                val name = when (density.densityAlias) {
                                    Targeting.ScreenDensity.DensityAlias.NODPI -> "nodpi"
                                    Targeting.ScreenDensity.DensityAlias.LDPI -> "ldpi"
                                    Targeting.ScreenDensity.DensityAlias.MDPI -> "mdpi"
                                    Targeting.ScreenDensity.DensityAlias.TVDPI -> "tvdpi"
                                    Targeting.ScreenDensity.DensityAlias.HDPI -> "hdpi"
                                    Targeting.ScreenDensity.DensityAlias.XHDPI -> "xhdpi"
                                    Targeting.ScreenDensity.DensityAlias.XXHDPI -> "xxhdpi"
                                    Targeting.ScreenDensity.DensityAlias.XXXHDPI -> "xxxhdpi"
                                    else -> return ParseApkSetResult.Error.DensityUnsupportedError
                                }
                                Pair(name, densitySplits)
                            }

                            // Since we've already ruled out all other APK targets, this implies that the APK
                            // targets no device attribute, which is probably invalid.
                            else -> return ParseApkSetResult.Error.ApkTargetsNothingError
                        }

                        // Avoid duplicate apk descriptions with the same targeting
                        if (dst[id] != null) {
                            return ParseApkSetResult.Error.MultipleApkDescriptionsError
                        }

                        // In manifests and in the split metadata, all the target parameters used are prefixed
                        // with "config". Keep this in mind when validating the target and
                        val targetSplitId = "config.${id}"
                        if (splitId != targetSplitId) {
                            return ParseApkSetResult.Error.SplitIdInconsistentError
                        }

                        val apkEntry =
                            apkEntries.filter { it.path == path && it.innerApk.manifest.split == targetSplitId }
                                .ifEmpty { return ParseApkSetResult.Error.NoSplitsFoundError }
                                .singleOrNull() ?: return ParseApkSetResult.Error.MultipleSplitsFoundError
                        dst[id] = apkEntry.path
                        consumed.add(apkEntry)
                    }
                }
                val newVariant = Variant(
                    minSdk,
                    baseSplit ?: return ParseApkSetResult.Error.BaseApkNotFoundError,
                    abiSplits,
                    langSplits,
                    densitySplits
                )
                variants.add(newVariant)
            }

            // It's possible that the metadata might have been tampered with in order to not list certain APKs.
            // In this case, we must make sure that all the Apks linked during variant parsing encompass
            // all the variants discovered during the initial parsing phase.
            if (consumed.size != apkEntries.size) {
                return ParseApkSetResult.Error.UnaccountedSplitsError
            }

            // Start combining the values discovered from each base APK into a single value (barring disagreements)
            val appId = appIds.singleOrNull() ?: return ParseApkSetResult.Error.AppIdInconsistentError
            if (metadata.packageName != appId) {
                return ParseApkSetResult.Error.AppIdInconsistentError
            }

            val versionCode = versionCodes.singleOrNull() ?: return ParseApkSetResult.Error.VersionCodeInconsistentError

            // versionName (and later usesSdk) are already nullable, so using singleOrNull would not allow us to handle
            // all possibilities. Replicate the functionality manually instead and return two different errors for
            // each type of "null" encountered.
            val versionName = if (versionNames.size == 1) {
                versionNames.first() ?: return ParseApkSetResult.Error.VersionNameUnspecifiedError
            } else {
                return ParseApkSetResult.Error.VersionNameInconsistentError
            }

            val usesSdk = if (usesSdks.size == 1) {
                usesSdks.first() ?: return ParseApkSetResult.Error.SdkUnspecifiedError
            } else {
                return ParseApkSetResult.Error.SdkInconsistentError
            }
            val minSdk = usesSdk.minSdkVersion
            val targetSdk = usesSdk.targetSdkVersion ?: return ParseApkSetResult.Error.TargetSdkUnspecifiedError
            val reviewIssues = reviewIssueSets.singleOrNull() ?:
                return ParseApkSetResult.Error.ReviewIssueInconsistentError

            val apkSet = ApkSet(
                appId,
                versionCode,
                versionName,
                minSdk,
                targetSdk,
                bundletoolVersion,
                reviewIssues,
                variants
            )

            return ParseApkSetResult.Ok(apkSet)
        }
    }
}
public class Variant(
    public val minSdk: Int,
    public val baseSplit: String,
    public val abiSplits: Map<String, String>,
    public val langSplits: Map<String, String>,
    public val densitySplits: Map<String, String>
)

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
         * An error was encountered in parsing the bundletool metadata
         */
        public object MetadataParseError : Error() {
            override val message: String = "bundletool metadata not valid"
        }

        /**
         * An error was encountered in parsing an APK
         */
        public data class ApkParseError(val error: ParseApkResult.Error) : Error() {
            override val message: String = error.message
        }

        /**
         * A mismatch exists between APK signing certificates
         */
        public object SigningCertMismatchError : Error() {
            override val message: String = "APK signing certificates don't match each other"
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
         * The apk set metadata (toc.pb) was not found
         */
        public object MetadataNotFoundError : Error() {
            override val message: String = "metadata was not found"
        }

        /**
         * No APKs were found
         */
        public object ApksNotFoundError : Error() {
            override val message: String = "no APKs found"
        }

        /**
         * An error was encountered in parsing the bundletool version
         */
        public object BundletoolVersionError : Error() {
            override val message: String = "invalid bundletool version"
        }

        /**
         * The apk set metadata used features not supported by accrescent
         */
        public object MetadataUnsupportedError : Error() {
            override val message: String = "metadata uses unsupported features"
        }

        /**
         * No variants (and thus no apks) were found in the apk set metadata
         */
        public object VariantsNotFoundError : Error() {
            override val message: String = "no variants were found"
        }

        /**
         * The variant targeting used parameters not supported by accrescent
         */
        public object VariantTargetingUnsupportedError : Error() {
            override val message: String = "variant uses unsupported targeting"
        }

        /**
         * A variant targeted no usable device attribute.
         */
        public object VariantTargetsNothingError : Error() {
            override val message: String = "variant targets no device attribute"
        }

        /**
         * The variant SDK targeting used a format unsupported by accrescent
         */
        public object VariantSDKUnsupportedError : Error() {
            override val message: String = "variant uses unsupported sdk targeting"
        }

        /**
         * Two variants were with the same number and/or minimum SDK were discovered
         */
        public object DuplicateVariantError : Error() {
            override val message: String = "duplicate variants found"
        }

        /**
         * The apk set used features unsupported by accrescent
         */
        public object ApkSetUnsupportedError : Error() {
            override val message: String = "apk set uses unsupported features"
        }

        /**
         * The apk set module used features unsupported by accrescent
         * (i.e. it was not using the default configuration)
         */
        public object ModuleUnsupportedError : Error() {
            override val message: String = "apk set uses unsupported module features"
        }

        /**
         * The apk set module targeting used parameters unsupported by accrescent
         * (i.e. it was not using the default configuration)
         */
        public object ModuleTargetingUnsupportedError : Error() {
            override val message: String = "apk set uses unsupported module targeting"
        }

        /**
         * The apk description used features unsupported by accrescent
         */
        public object ApkDescriptionUnsupportedError : Error() {
            override val message: String = "apk description uses unsupported features"
        }

        /**
         * A base APK description had a split ID that was inconsistent with it's split APK properties
         */
        public object BaseApkDescriptionInconsistentError : Error() {
            override val message: String = "base apk description is not consistent with itself"
        }

        /**
         * Multiple base apk descriptions were encountered in a single variant
         */
        public object MultipleBaseApkDescriptionsError : Error() {
            override val message: String = "multiple base apk descriptions found"
        }

        /**
         * No base splits were found for the given apk description's path and split ID
         */
        public object NoBaseSplitsFoundError : Error() {
            override val message: String = "no base splits found for apk description"
        }

        /**
         * Several base splits were found for the given apk description's path and split ID
         */
        public object MultipleBaseSplitsFoundError : Error() {
            override val message: String = "multiple base splits found for apk description"
        }

        public object DuplicateSplitPathsError : Error() {
            override val message: String = "several splits in a variant point to the same apk"
        }


        /**
         * The apk description's targeting used parameters unsupported by accrescent
         */
        public object ApkTargetingUnsupportedError : Error() {
            override val message: String = "apk description uses unsupported targeting"
        }

        /**
         * The apk ABI targeting used a format unsupported by accrescent
         */
        public object AbiUnsupportedError : Error() {
            override val message: String = "apk targeting uses unsupported abi"
        }

        /**
         * The apk language targeting used a format unsupported by accrescent
         */
        public object LangUnsupportedError : Error() {
            override val message: String = "apk targeting uses unsupported language"
        }

        /**
         * The apk density targeting used a format unsupported by accrescent
         */
        public object DensityUnsupportedError : Error() {
            override val message: String = "apk targeting uses unsupported density"
        }

        /**
         * The apk did not target any usable device attribute.
         */
        public object ApkTargetsNothingError : Error() {
            override val message: String = "apk targets no device attribute"
        }

        /**
         * Multiple apk descriptions of the same targeting were encountered in a single variant
         */
        public object MultipleApkDescriptionsError : Error() {
            override val message: String = "multiple apk descriptions found"
        }

        /**
         * The split ID was inconsistent between the apk description and the split apk's manifest
         */
        public object SplitIdInconsistentError : Error() {
            override val message: String = "apk description and apk manifest do not have the same split ids"
        }

        /**
         * No splits were found for the given apk description's path and split ID
         */
        public object NoSplitsFoundError : Error() {
            override val message: String = "no splits found for apk description"
        }

        /**
         * Multiple splits were found for the given apk descriptions path and split ID
         */
        public object MultipleSplitsFoundError : Error() {
            override val message: String = "multiple splits found for apk description"
        }

        /**
         * No base APK was found for a variant
         */
        public object BaseApkNotFoundError : Error() {
            override val message: String = "no base APK found for variant"
        }

        /**
         * There were some splits in the apk set that were not accounted for by the split metadata
         */
        public object UnaccountedSplitsError : Error() {
            override val message: String = "one or more splits unaccounted by metadata"
        }

        /**
         * The app id was inconsistent between inside the base splits of each variant, or it was inconsistent between
         * the base splits and metadata
         */
        public object AppIdInconsistentError : Error() {
            override val message: String = "app id is not the same between base splits and metadata"
        }

        /**
         * The version code was inconsistent between the base splits of each variant
         */
        public object VersionCodeInconsistentError : Error() {
            override val message: String = "version code is not the same between base splits"
        }

        /**
         * The version name was not specified in any of the base splits of each variant
         */
        public object VersionNameUnspecifiedError : Error() {
            override val message: String = "base splits don't specify a version name"
        }

        /**
         * The version name was inconsistent between the base splits of each variant
         */
        public object VersionNameInconsistentError : Error() {
            override val message: String = "version name is not the same between base splits"
        }

        /**
         * The sdk version (including min and target sdk) was not specified in any of the base splits
         * of each variant
         */
        public object SdkUnspecifiedError : Error() {
            override val message: String = "base splits don't specify a sdk"
        }

        /**
         * The sdk version (including min and target sdk) was inconsistent between the base splits of each variant
         */
        public object SdkInconsistentError : Error() {
            override val message: String = "sdk is not the same between splits"
        }

        /**
         * The target sdk version was not specified in any of the base splits of each variant
         */
        public object TargetSdkUnspecifiedError : Error() {
            override val message: String = "base splits don't specify a target SDK"
        }

        /**
         * The review issues were inconsistent between the base splits of each variant
         */
        public object ReviewIssueInconsistentError : Error() {
            override val message: String = "review issues are not the same between base splits"
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