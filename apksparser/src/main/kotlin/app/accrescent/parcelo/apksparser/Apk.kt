package app.accrescent.parcelo.apksparser

import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import com.android.apksig.zip.ZipFormatException
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import java.nio.ByteBuffer
import java.security.cert.X509Certificate

/**
 * An Android application package (APK).
 *
 * This class can only represent a subset of APKs. That is, there exist valid APKs which cannot be
 * represented by this class, as it makes some additional security checks which are not strictly
 * necessary for validating the package. Specifically, this class can only represent APKs which:
 *
 * - are signed with a signature scheme version greater than v1
 * - are signed only by a non-debug certificate or certificates
 * - pass signature verification
 */
public class Apk private constructor(
    public val manifest: AndroidManifest,
    public val signerCertificates: List<X509Certificate>,
) {
    public companion object {
        private const val ANDROID_MANIFEST = "AndroidManifest.xml"

        /**
         * Parses an APK from the provided data
         */
        public fun parse(data: ByteArray): ParseApkResult {
            val dataSource = DataSources.asDataSource(ByteBuffer.wrap(data))

            // Verify the APK's signature
            val sigCheckResult = try {
                ApkVerifier.Builder(dataSource).build().verify()
            } catch (e: ApkFormatException) {
                return when (e.cause) {
                    is ZipFormatException -> ParseApkResult.Error.ZipFormatError
                    else -> ParseApkResult.Error.ApkFormatError
                }
            }
            val signerCertificates = if (sigCheckResult.isVerified) {
                if (!(sigCheckResult.isVerifiedUsingV2Scheme || sigCheckResult.isVerifiedUsingV3Scheme)) {
                    return ParseApkResult.Error.SignatureVersionError
                } else if (sigCheckResult.signerCertificates.any { it.isDebug() }) {
                    return ParseApkResult.Error.DebugCertificateError
                } else {
                    sigCheckResult.signerCertificates
                }
            } else {
                return ParseApkResult.Error.SignatureVerificationError
            }

            // Parse the Android manifest
            val manifest = try {
                val manifestBytes = ApkUtils.getAndroidManifest(dataSource).moveToByteArray()
                val decodedManifest = BinaryXmlParser.decodeXml(ANDROID_MANIFEST, manifestBytes)
                manifestReader.readValue<AndroidManifest>(decodedManifest)
            } catch (e: ApkFormatException) {
                return ParseApkResult.Error.ApkFormatError
            } catch (e: ValueInstantiationException) {
                return ParseApkResult.Error.AndroidManifestError
            }

            return ParseApkResult.Ok(Apk(manifest, signerCertificates))
        }
    }
}

/**
 * Representation of the result of attempting to parse an APK
 */
public sealed class ParseApkResult {
    /**
     * The result of successful parsing
     */
    public data class Ok(val apk: Apk) : ParseApkResult()

    /**
     * The result of failed parsing
     */
    public sealed class Error : ParseApkResult() {
        /**
         * A message describing the error
         */
        public abstract val message: String

        /**
         * The APK is not a well-formed ZIP archive
         */
        public object ZipFormatError : Error() {
            override val message: String = "APK is not a well-formed ZIP archive"
        }

        /**
         * The APK is not well-formed. The specific reason is not specified.
         */
        public object ApkFormatError : Error() {
            override val message: String = "APK is not well-formed"
        }

        /**
         * The APK was not signed with a required signature version
         */
        public object SignatureVersionError : Error() {
            override val message: String = "APK not signed with a required signature version"
        }

        /**
         * The APK is signed with a debug certificate
         */
        public object DebugCertificateError : Error() {
            override val message: String = "APK is signed with a debug certificate"
        }

        /**
         * Signature verification failed. In other words, the signature is invalid for this APK.
         */
        public object SignatureVerificationError : Error() {
            override val message: String = "signature verification failed"
        }

        /**
         * The Android manifest is not valid
         */
        public object AndroidManifestError : Error() {
            override val message: String = "invalid Android manifest"
        }
    }
}

/**
 * Returns whether this is a debug certificate generated by the Android SDK tools
 */
private fun X509Certificate.isDebug(): Boolean {
    return subjectX500Principal.name == "C=US,O=Android,CN=Android Debug"
}
