package app.accrescent.validation

import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import com.android.ide.common.xml.AndroidManifestParser
import com.android.io.IAbstractFile
import com.android.tools.apk.analyzer.BinaryXmlParser
import io.ktor.util.moveToByteArray
import org.xml.sax.SAXException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

data class ApkSetMetadata(val appId: String, val versionCode: Int, val versionName: String)

const val ANDROID_MANIFEST = "AndroidManifest.xml"

/**
 * Parses an APK set into its metadata
 *
 * For now this function attempts to determine whether the APK set is valid on a best-effort
 * basis, so it may accept files which are not strictly valid APK sets. However, any APK set it
 * rejects is certainly invalid. It currently accepts the given file as a valid APK set according
 * to the following criteria:
 *
 * - the input file is a valid ZIP
 * - a valid APK is a ZIP with each of the following:
 *   - a v2 or v3 APK signature which passes verification
 *   - a valid Android manifest at the expected path
 * - all non-directory entries in said ZIP except for "toc.pb" are valid APKs
 * - the input ZIP contains at least one APK
 * - all APKs have the same app ID and version code
 * - at least one APK specifies a version name
 *
 * @return metadata describing the APK set and the app it represents
 * @throws InvalidApkSetException the APK set is invalid
 */
fun parseApkSet(file: InputStream): ApkSetMetadata {
    var metadata: ApkSetMetadata? = null

    ZipInputStream(file).use { zip ->
        generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
            // Ignore metadata
            if (entry.name == "toc.pb") return@forEach

            val apk = DataSources.asDataSource(ByteBuffer.wrap(zip.readAllBytes()))

            val sigCheckResult = try {
                ApkVerifier.Builder(apk).build().verify()
            } catch (e: ApkFormatException) {
                throw InvalidApkSetException("an APK is malformed")
            }

            if (sigCheckResult.isVerified) {
                if (!(sigCheckResult.isVerifiedUsingV2Scheme || sigCheckResult.isVerifiedUsingV3Scheme)) {
                    throw InvalidApkSetException("APK signature isn't at least v2 or v3")
                }
            } else {
                throw InvalidApkSetException("APK signature doesn't verify")
            }

            // Parse the Android manifest
            val manifest = try {
                val manifestBytes = ApkUtils.getAndroidManifest(apk).moveToByteArray()
                BinaryXmlParser.decodeXml(ANDROID_MANIFEST, manifestBytes).inputStream()
                    .use { AndroidManifestParser.parse(it.toIAbstractFile(), true, null) }
            } catch (e: ApkFormatException) {
                throw InvalidApkSetException("an APK is malformed")
            } catch (e: SAXException) {
                throw InvalidApkSetException("invalid Android manifest")
            }

            // Pin the app metadata on the first manifest parsed to ensure all split APKs have the
            // same app ID and version code.
            //
            // Since the version name is only included in the base APK, we update it
            // opportunistically and freak out if it's still empty by the time we're done going
            // through all the APKs. There's no reason to pin it since it has no effect on
            // installation.
            if (metadata == null) {
                metadata = ApkSetMetadata(manifest.`package`, manifest.versionCode, "")
            } else {
                // Check that the metadata is the same as that previously pinned (sans the version
                // name for reasons described above).
                //
                // We can non-null assert the metadata here since the changing closure is called
                // sequentially.
                if (manifest.`package` != metadata!!.appId || manifest.versionCode != metadata!!.versionCode) {
                    throw InvalidApkSetException("APK manifest info is not consistent across all APKs")
                }

                // Update the version name if it exists (i.e., if this is the base APK)
                if (manifest.versionName != null) {
                    metadata = metadata!!.copy(versionName = manifest.versionName)
                }
            }
        }
    }

    // If nothing set the version name, freak out
    if (metadata?.versionName == "") {
        throw InvalidApkSetException("no APKs specified a version name")
    }

    return metadata ?: throw InvalidApkSetException("no APKs found")
}

/**
 * Hack utility method to convert an InputStream to an IAbstractFile
 *
 * This is a hack to satisfy the API contract of AndroidManifestParser.parse(), which requires an
 * IAbstractFile as a parameter. In reality, it uses none of that interface's methods besides
 * getContents() to get the underlying InputStream. Thus, we can convert an InputStream into an
 * IAbstractFile as long as we don't care that the rest of the interface isn't properly
 * implemented. Of course, this means that the IAbstractFile returned by this function shouldn't
 * be used for anything which _actually_ needs it, but only for that which needs the underlying
 * InputStream.
 */
private fun InputStream.toIAbstractFile(): IAbstractFile {
    val inputStream = this

    return object : IAbstractFile {
        override fun getOsLocation() = ""
        override fun exists() = true
        override fun getContents() = inputStream
        override fun setContents(source: InputStream) {}
        override fun getOutputStream() = null
    }
}

class InvalidApkSetException(message: String) : Exception(message)
