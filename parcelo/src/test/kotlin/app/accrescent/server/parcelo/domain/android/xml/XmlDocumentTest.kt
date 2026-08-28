// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.android.xml

import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.domain.uri.Uri
import arrow.core.Some
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

class XmlDocumentTest {
    @ParameterizedTest
    @MethodSource("fromBinaryXmlTestCases")
    fun `fromBinaryXml returns expected document`(testCase: FromBinaryXmlTestCase) {
        val result = XmlDocument.fromBinaryXml(ByteBuffer.wrap(testCase.binaryXml.copyToByteArray())).unwrap()

        assertEquals(testCase.expectedDocument, result)
    }

    // String attributes have both a typed value and a raw value, which are both string pool
    // indices. AOSP doesn't consistently prefer one over the other when reading the attribute's
    // value, so to prevent parser differential vulnerabilities, we reject documents with string
    // attributes which have different typed and raw values.
    @Test
    fun `fromBinaryXml rejects string attribute whose typed and raw values differ`() {
        // You can see that this manifest's "package" attribute typed value is different from its
        // raw value with `aapt2 dump xmltree`, which will display
        // `A: package="com.example.decoy" (Raw: "com.example.real")`.
        val binaryXml = binaryXmlTestData("mismatched-string-attribute-manifest.axml.gz")

        val result = XmlDocument.fromBinaryXml(ByteBuffer.wrap(binaryXml.copyToByteArray()))

        assertTrue(result.isLeft())
    }

    data class FromBinaryXmlTestCase(val binaryXml: Bytes, val expectedDocument: XmlDocument)

    companion object {
        @JvmStatic
        fun fromBinaryXmlTestCases(): List<FromBinaryXmlTestCase> {
            return listOf(
                FromBinaryXmlTestCase(
                    binaryXml = binaryXmlTestData("accrescent-0.28.1-master-manifest.axml.gz"),
                    expectedDocument = ACCRESCENT_MASTER_MANIFEST,
                )
            )
        }

        private fun binaryXmlTestData(resourceName: String): Bytes {
            return Companion::class.java.classLoader
                .getResourceAsStream(resourceName)!!
                .use { GZIPInputStream(it).use(InputStream::readBytes) }
                .let(::Bytes)
        }

        private val ANDROID_NAMESPACE_URI =
            Uri.fromUString("http://schemas.android.com/apk/res/android".u).unwrap()

        /** An `android`-namespaced expanded name. */
        private fun androidName(localName: String): XmlExpandedName =
            XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString(localName.u).unwrap())

        /** An `android`-namespaced attribute, whose name carries a resource ID. */
        private fun androidAttribute(
            localName: String,
            resourceId: UInt,
            value: ResourceValue,
        ): XmlAttribute =
            XmlAttribute(
                XmlAttributeId(androidName(localName), Some(ResourceId(resourceId))),
                value,
            )

        private fun element(
            name: String,
            attributes: List<XmlAttribute> = emptyList(),
            children: List<XmlElement> = emptyList(),
        ): XmlElement = XmlElement(
            unqualifiedName(name),
            XmlAttributes.fromList(attributes).unwrap(),
            children,
        )

        /** An element carrying only an `android:name` string attribute. */
        private fun namedElement(elementName: String, name: String): XmlElement = element(
            elementName,
            listOf(androidAttribute("name", 0x01010003u, ResourceValue.String(name.u))),
        )

        /** A `meta-data` element carrying an `android:name` and an `android:value`. */
        private fun metaData(name: String, value: ResourceValue): XmlElement = element(
            "meta-data",
            listOf(
                androidAttribute("name", 0x01010003u, ResourceValue.String(name.u)),
                androidAttribute("value", 0x01010024u, value),
            ),
        )

        /**
         * The expected document for `accrescent-0.28.1-master-manifest.axml.gz`, transcribed from
         * `aapt2 dump xmltree` cross-checked against the raw `Res_value` data types: decimal
         * integers map to [ResourceValue.IntDec], strings to [ResourceValue.String], booleans to
         * [ResourceValue.Bool], and references and hexadecimal integers (both unrecognized types)
         * to [ResourceValue.Unsupported] holding the raw `data` word. Attribute resource IDs are
         * transcribed from the document's resource map.
         */
        private val ACCRESCENT_MASTER_MANIFEST = XmlDocument(
            root = element(
                "manifest",
                listOf(
                    androidAttribute("versionCode", 0x0101021Bu, ResourceValue.IntDec(55)),
                    androidAttribute("versionName", 0x0101021Cu, ResourceValue.String("0.28.1".u)),
                    androidAttribute("compileSdkVersion", 0x01010572u, ResourceValue.IntDec(36)),
                    androidAttribute(
                        "compileSdkVersionCodename",
                        0x01010573u,
                        ResourceValue.String("16".u),
                    ),
                    androidAttribute(
                        "requiredSplitTypes",
                        0x0101064Eu,
                        ResourceValue.String("base__abi,base__density".u),
                    ),
                    androidAttribute("splitTypes", 0x0101064Fu, ResourceValue.String("".u)),
                    unqualifiedAttribute("package", ResourceValue.String("app.accrescent.client".u)),
                    unqualifiedAttribute("platformBuildVersionCode", ResourceValue.IntDec(36)),
                    unqualifiedAttribute("platformBuildVersionName", ResourceValue.IntDec(16)),
                ),
                listOf(
                    element(
                        "uses-sdk",
                        listOf(
                            androidAttribute("minSdkVersion", 0x0101020Cu, ResourceValue.IntDec(29)),
                            androidAttribute(
                                "targetSdkVersion",
                                0x01010270u,
                                ResourceValue.IntDec(36),
                            ),
                        ),
                    ),
                    element(
                        "uses-feature",
                        listOf(
                            androidAttribute(
                                "name",
                                0x01010003u,
                                ResourceValue.String("android.hardware.touchscreen".u),
                            ),
                            androidAttribute("required", 0x0101028Eu, ResourceValue.Bool(false)),
                        ),
                    ),
                    element(
                        "uses-feature",
                        listOf(
                            androidAttribute(
                                "name",
                                0x01010003u,
                                ResourceValue.String("android.software.leanback".u),
                            ),
                            androidAttribute("required", 0x0101028Eu, ResourceValue.Bool(false)),
                        ),
                    ),
                    namedElement("uses-permission", "android.permission.ACCESS_NETWORK_STATE"),
                    namedElement("uses-permission", "android.permission.ENFORCE_UPDATE_OWNERSHIP"),
                    namedElement("uses-permission", "android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
                    namedElement("uses-permission", "android.permission.INTERNET"),
                    namedElement("uses-permission", "android.permission.POST_NOTIFICATIONS"),
                    namedElement("uses-permission", "android.permission.QUERY_ALL_PACKAGES"),
                    namedElement("uses-permission", "android.permission.REQUEST_DELETE_PACKAGES"),
                    namedElement("uses-permission", "android.permission.REQUEST_INSTALL_PACKAGES"),
                    namedElement(
                        "uses-permission",
                        "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
                    ),
                    namedElement("uses-permission", "android.permission.WAKE_LOCK"),
                    namedElement("uses-permission", "android.permission.RECEIVE_BOOT_COMPLETED"),
                    namedElement("uses-permission", "android.permission.FOREGROUND_SERVICE"),
                    element(
                        "permission",
                        listOf(
                            androidAttribute(
                                "name",
                                0x01010003u,
                                ResourceValue.String(
                                    "app.accrescent.client.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION".u,
                                ),
                            ),
                            androidAttribute(
                                "protectionLevel",
                                0x01010009u,
                                ResourceValue.Unsupported(2u),
                            ),
                        ),
                    ),
                    namedElement(
                        "uses-permission",
                        "app.accrescent.client.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                    ),
                    element(
                        "application",
                        listOf(
                            androidAttribute(
                                "theme",
                                0x01010000u,
                                ResourceValue.Unsupported(0x7F110253u),
                            ),
                            androidAttribute(
                                "label",
                                0x01010001u,
                                ResourceValue.Unsupported(0x7F100031u),
                            ),
                            androidAttribute(
                                "icon",
                                0x01010002u,
                                ResourceValue.Unsupported(0x7F0D0000u),
                            ),
                            androidAttribute(
                                "name",
                                0x01010003u,
                                ResourceValue.String("app.accrescent.client.Accrescent".u),
                            ),
                            androidAttribute("supportsRtl", 0x010103AFu, ResourceValue.Bool(true)),
                            androidAttribute(
                                "banner",
                                0x010103F2u,
                                ResourceValue.Unsupported(0x7F0700F4u),
                            ),
                            androidAttribute(
                                "extractNativeLibs",
                                0x010104EAu,
                                ResourceValue.Bool(false),
                            ),
                            androidAttribute(
                                "fullBackupContent",
                                0x010104EBu,
                                ResourceValue.Unsupported(0x7F130001u),
                            ),
                            androidAttribute(
                                "roundIcon",
                                0x0101052Cu,
                                ResourceValue.Unsupported(0x7F0D0000u),
                            ),
                            androidAttribute(
                                "appComponentFactory",
                                0x0101057Au,
                                ResourceValue.String("androidx.core.app.CoreComponentFactory".u),
                            ),
                            androidAttribute("memtagMode", 0x01010624u, ResourceValue.IntDec(1)),
                            androidAttribute(
                                "dataExtractionRules",
                                0x0101063Eu,
                                ResourceValue.Unsupported(0x7F130000u),
                            ),
                            androidAttribute(
                                "enableOnBackInvokedCallback",
                                0x0101066Cu,
                                ResourceValue.Bool(true),
                            ),
                            androidAttribute(
                                "intentMatchingFlags",
                                0x010106A9u,
                                ResourceValue.Unsupported(2u),
                            ),
                        ),
                        listOf(
                            element(
                                "activity",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String("app.accrescent.client.ui.MainActivity".u),
                                    ),
                                    androidAttribute("exported", 0x01010010u, ResourceValue.Bool(true)),
                                ),
                                listOf(
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement("action", "android.intent.action.MAIN"),
                                            namedElement(
                                                "category",
                                                "android.intent.category.APP_MARKET",
                                            ),
                                            namedElement(
                                                "category",
                                                "android.intent.category.DEFAULT",
                                            ),
                                            namedElement(
                                                "category",
                                                "android.intent.category.LAUNCHER",
                                            ),
                                            namedElement(
                                                "category",
                                                "android.intent.category.LEANBACK_LAUNCHER",
                                            ),
                                        ),
                                    ),
                                    element(
                                        "intent-filter",
                                        listOf(
                                            androidAttribute(
                                                "autoVerify",
                                                0x010104EEu,
                                                ResourceValue.Bool(true),
                                            ),
                                        ),
                                        listOf(
                                            namedElement("action", "android.intent.action.VIEW"),
                                            namedElement(
                                                "category",
                                                "android.intent.category.DEFAULT",
                                            ),
                                            namedElement(
                                                "category",
                                                "android.intent.category.BROWSABLE",
                                            ),
                                            element(
                                                "data",
                                                listOf(
                                                    androidAttribute(
                                                        "scheme",
                                                        0x01010027u,
                                                        ResourceValue.String("http".u),
                                                    ),
                                                ),
                                            ),
                                            element(
                                                "data",
                                                listOf(
                                                    androidAttribute(
                                                        "scheme",
                                                        0x01010027u,
                                                        ResourceValue.String("https".u),
                                                    ),
                                                ),
                                            ),
                                            element(
                                                "data",
                                                listOf(
                                                    androidAttribute(
                                                        "host",
                                                        0x01010028u,
                                                        ResourceValue.String("accrescent.app".u),
                                                    ),
                                                ),
                                            ),
                                            element(
                                                "data",
                                                listOf(
                                                    androidAttribute(
                                                        "pathPrefix",
                                                        0x0101002Bu,
                                                        ResourceValue.String("/app".u),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "android.intent.action.SHOW_APP_INFO",
                                            ),
                                            namedElement(
                                                "category",
                                                "android.intent.category.DEFAULT",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            element(
                                "provider",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String("androidx.startup.InitializationProvider".u),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "authorities",
                                        0x01010018u,
                                        ResourceValue.String("app.accrescent.client.androidx-startup".u),
                                    ),
                                ),
                                listOf(
                                    metaData(
                                        "androidx.emoji2.text.EmojiCompatInitializer",
                                        ResourceValue.String("androidx.startup".u),
                                    ),
                                    metaData(
                                        "androidx.lifecycle.ProcessLifecycleInitializer",
                                        ResourceValue.String("androidx.startup".u),
                                    ),
                                    metaData(
                                        "okhttp3.internal.platform.PlatformInitializer",
                                        ResourceValue.String("androidx.startup".u),
                                    ),
                                    metaData(
                                        "androidx.profileinstaller.ProfileInstallerInitializer",
                                        ResourceValue.String("androidx.startup".u),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "app.accrescent.client.receivers.AppUninstallBroadcastReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "app.accrescent.client.receivers.InstallerSessionCommitBroadcastReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                            ),
                            element(
                                "service",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.work.impl.foreground.SystemForegroundService".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "enabled",
                                        0x0101000Eu,
                                        ResourceValue.Unsupported(0x7F040003u),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "foregroundServiceType",
                                        0x01010599u,
                                        ResourceValue.Unsupported(1u),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "app.accrescent.client.receivers.UnarchiveRequestBroadcastReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                                listOf(
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "android.intent.action.UNARCHIVE_PACKAGE",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "app.accrescent.client.receivers.UnarchiveResponseBroadcastReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                            ),
                            element(
                                "service",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.work.impl.background.systemjob.SystemJobService".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "permission",
                                        0x01010006u,
                                        ResourceValue.String("android.permission.BIND_JOB_SERVICE".u),
                                    ),
                                    androidAttribute(
                                        "enabled",
                                        0x0101000Eu,
                                        ResourceValue.Unsupported(0x7F040004u),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(true),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.work.impl.utils.ForceStopRunnable\$BroadcastReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "enabled",
                                        0x0101000Eu,
                                        ResourceValue.Bool(true),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.work.impl.background.systemalarm.RescheduleReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "enabled",
                                        0x0101000Eu,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                                listOf(
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "android.intent.action.BOOT_COMPLETED",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.work.impl.diagnostics.DiagnosticsReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "permission",
                                        0x01010006u,
                                        ResourceValue.String("android.permission.DUMP".u),
                                    ),
                                    androidAttribute(
                                        "enabled",
                                        0x0101000Eu,
                                        ResourceValue.Bool(true),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(true),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                                listOf(
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "androidx.work.diagnostics.REQUEST_DIAGNOSTICS",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            element(
                                "activity",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.compose.ui.tooling.PreviewActivity".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(true),
                                    ),
                                ),
                            ),
                            element(
                                "service",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.room.MultiInstanceInvalidationService".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(false),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(true),
                                    ),
                                ),
                            ),
                            element(
                                "receiver",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String(
                                            "androidx.profileinstaller.ProfileInstallReceiver".u,
                                        ),
                                    ),
                                    androidAttribute(
                                        "permission",
                                        0x01010006u,
                                        ResourceValue.String("android.permission.DUMP".u),
                                    ),
                                    androidAttribute(
                                        "enabled",
                                        0x0101000Eu,
                                        ResourceValue.Bool(true),
                                    ),
                                    androidAttribute(
                                        "exported",
                                        0x01010010u,
                                        ResourceValue.Bool(true),
                                    ),
                                    androidAttribute(
                                        "directBootAware",
                                        0x01010505u,
                                        ResourceValue.Bool(false),
                                    ),
                                ),
                                listOf(
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "androidx.profileinstaller.action.INSTALL_PROFILE",
                                            ),
                                        ),
                                    ),
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "androidx.profileinstaller.action.SKIP_FILE",
                                            ),
                                        ),
                                    ),
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "androidx.profileinstaller.action.SAVE_PROFILE",
                                            ),
                                        ),
                                    ),
                                    element(
                                        "intent-filter",
                                        children = listOf(
                                            namedElement(
                                                "action",
                                                "androidx.profileinstaller.action.BENCHMARK_OPERATION",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            metaData(
                                "com.android.vending.splits.required",
                                ResourceValue.Bool(true),
                            ),
                            element(
                                "meta-data",
                                listOf(
                                    androidAttribute(
                                        "name",
                                        0x01010003u,
                                        ResourceValue.String("com.android.vending.splits".u),
                                    ),
                                    androidAttribute(
                                        "resource",
                                        0x01010025u,
                                        ResourceValue.Unsupported(0x7F130004u),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
