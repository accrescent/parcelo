// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.android

import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.core.unwrapErr
import app.accrescent.server.parcelo.domain.android.xml.AsciiNcName
import app.accrescent.server.parcelo.domain.android.xml.ResourceId
import app.accrescent.server.parcelo.domain.android.xml.ResourceValue
import app.accrescent.server.parcelo.domain.android.xml.XmlAttribute
import app.accrescent.server.parcelo.domain.android.xml.XmlAttributeId
import app.accrescent.server.parcelo.domain.android.xml.XmlAttributes
import app.accrescent.server.parcelo.domain.android.xml.XmlDocument
import app.accrescent.server.parcelo.domain.android.xml.XmlElement
import app.accrescent.server.parcelo.domain.android.xml.XmlExpandedName
import app.accrescent.server.parcelo.domain.uri.Uri
import arrow.core.None
import arrow.core.Some
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidManifestTest {
    @Test
    fun `fromXmlDocument returns expected manifest for full valid document`() {
        val manifest = AndroidManifest.fromXmlDocument(FULL_VALID_DOCUMENT).unwrap()

        assertEquals(
            AndroidManifest(
                applicationId = ApplicationId.fromUString("com.example.app".u).unwrap(),
                splitId = None,
                versionCode = VersionCode.fromInt(1).unwrap(),
                versionName = Some(VersionName.fromUString("1.0".u).unwrap()),
                minSdkVersion = SdkVersion.fromInt(29).unwrap(),
                targetSdkVersion = SdkVersion.fromInt(37).unwrap(),
                permissions = mapOf(
                    NameAttribute.fromUString("android.permission.INTERNET".u).unwrap() to None,
                    NameAttribute.fromUString("android.permission.READ_EXTERNAL_STORAGE".u).unwrap()
                            to Some(SdkVersion.fromInt(32).unwrap()),
                ),
            ),
            manifest,
        )
    }

    @Test
    fun `fromXmlDocument returns DuplicatePermission when the same permission is declared twice`() {
        val document = withManifestChildren(
            permissionElement("uses-permission", "android.permission.INTERNET"),
            permissionElement("uses-permission", "android.permission.INTERNET"),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.DuplicatePermission, error)
    }

    @Test
    fun `fromXmlDocument returns DuplicatePermission when a permission is declared in both uses-permission and uses-permission-sdk-23`() {
        val document = withManifestChildren(
            permissionElement("uses-permission", "android.permission.INTERNET"),
            permissionElement("uses-permission-sdk-23", "android.permission.INTERNET"),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.DuplicatePermission, error)
    }

    @Test
    fun `fromXmlDocument parses uses-permission-sdk-m elements as permissions`() {
        val document = withManifestChildren(
            permissionElement("uses-permission-sdk-m", "android.permission.CAMERA"),
        )

        val manifest = AndroidManifest.fromXmlDocument(document).unwrap()

        assertEquals(
            None,
            manifest.permissions[NameAttribute.fromUString("android.permission.CAMERA".u).unwrap()],
        )
    }

    @Test
    fun `fromXmlDocument returns DebuggableTrue when application element has debuggable set to true`() {
        val document = withApplicationAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("debuggable".u).unwrap()),
                    Some(DEBUGGABLE_RESOURCE_ID),
                ),
                ResourceValue.Bool(true),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.DebuggableTrue, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when application element's debuggable attribute is not a boolean`() {
        val document = withApplicationAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("debuggable".u).unwrap()),
                    Some(DEBUGGABLE_RESOURCE_ID),
                ),
                ResourceValue.String("notaboolean".u),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument succeeds when application element has debuggable set to false`() {
        val document = withApplicationAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("debuggable".u).unwrap()),
                    Some(DEBUGGABLE_RESOURCE_ID),
                ),
                ResourceValue.Bool(false),
            ),
        )

        AndroidManifest.fromXmlDocument(document).unwrap()
    }

    @Test
    fun `fromXmlDocument returns TestOnlyTrue when application element has testOnly set to true`() {
        val document = withApplicationAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("testOnly".u).unwrap()),
                    Some(TEST_ONLY_RESOURCE_ID),
                ),
                ResourceValue.Bool(true),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.TestOnlyTrue, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when application element's testOnly attribute is not a boolean`() {
        val document = withApplicationAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("testOnly".u).unwrap()),
                    Some(TEST_ONLY_RESOURCE_ID),
                ),
                ResourceValue.String("notaboolean".u),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument succeeds when application element has testOnly set to false`() {
        val document = withApplicationAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("testOnly".u).unwrap()),
                    Some(TEST_ONLY_RESOURCE_ID),
                ),
                ResourceValue.Bool(false),
            ),
        )

        AndroidManifest.fromXmlDocument(document).unwrap()
    }

    @Test
    fun `fromXmlDocument returns InvalidApplicationId for invalid application ID`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(None, AsciiNcName.fromUString("package".u).unwrap()),
                            None,
                        ),
                        ResourceValue.String("".u),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.InvalidApplicationId, error)
    }

    @Test
    fun `fromXmlDocument returns expected split when the manifest has a split attribute`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(None, AsciiNcName.fromUString("split".u).unwrap()),
                            None,
                        ),
                        ResourceValue.String("config.xxhdpi".u),
                    ),
                ),
            ),
        )

        val manifest = AndroidManifest.fromXmlDocument(document).unwrap()

        assertEquals(Some("config.xxhdpi".u), manifest.splitId)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when the split attribute is not a string`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(None, AsciiNcName.fromUString("split".u).unwrap()),
                            None,
                        ),
                        ResourceValue.IntDec(0),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument defaults minSdkVersion to 1 when uses-sdk is not present`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                children = FULL_VALID_DOCUMENT.root.children
                    .filterNot {
                        it.name == XmlExpandedName(None, AsciiNcName.fromUString("uses-sdk".u).unwrap())
                    },
            ),
        )

        val manifest = AndroidManifest.fromXmlDocument(document).unwrap()

        assertEquals(1, manifest.minSdkVersion.value)
    }

    @Test
    fun `fromXmlDocument defaults minSdkVersion to 1 when minSdkVersion attribute is not present`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                children = FULL_VALID_DOCUMENT.root.children.map {
                    if (it.name == XmlExpandedName(None, AsciiNcName.fromUString("uses-sdk".u).unwrap())) {
                        it.copy(
                            attributes = it.attributes
                                .without(
                                    XmlExpandedName(
                                        Some(ANDROID_NAMESPACE_URI),
                                        AsciiNcName.fromUString("minSdkVersion".u).unwrap(),
                                    ),
                                ),
                        )
                    } else {
                        it
                    }
                },
            ),
        )

        val manifest = AndroidManifest.fromXmlDocument(document).unwrap()

        assertEquals(1, manifest.minSdkVersion.value)
    }

    @Test
    fun `fromXmlDocument defaults targetSdkVersion to minSdkVersion when targetSdkVersion attribute is not present`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                children = FULL_VALID_DOCUMENT.root.children.map {
                    if (it.name == XmlExpandedName(None, AsciiNcName.fromUString("uses-sdk".u).unwrap())) {
                        it.copy(
                            attributes = it.attributes
                                .without(
                                    XmlExpandedName(
                                        Some(ANDROID_NAMESPACE_URI),
                                        AsciiNcName.fromUString("targetSdkVersion".u).unwrap(),
                                    ),
                                ),
                        )
                    } else {
                        it
                    }
                },
            ),
        )

        val manifest = AndroidManifest.fromXmlDocument(document).unwrap()

        assertEquals(manifest.minSdkVersion.value, manifest.targetSdkVersion.value)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for non-integer minimum SDK version`() {
        val document = withUsesSdkAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("minSdkVersion".u).unwrap()),
                    Some(MIN_SDK_VERSION_RESOURCE_ID),
                ),
                ResourceValue.String("notaninteger".u),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for out-of-range minimum SDK version`() {
        val document = withUsesSdkAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("minSdkVersion".u).unwrap()),
                    Some(MIN_SDK_VERSION_RESOURCE_ID),
                ),
                ResourceValue.IntDec(0),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for non-integer permission maxSdkVersion`() {
        val document = withManifestChildren(
            XmlElement(
                name = XmlExpandedName(None, AsciiNcName.fromUString("uses-permission".u).unwrap()),
                attributes = attributes(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("name".u).unwrap()),
                            Some(NAME_RESOURCE_ID),
                        ),
                        ResourceValue.String("android.permission.INTERNET".u),
                    ),
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("maxSdkVersion".u).unwrap(),
                            ),
                            Some(MAX_SDK_VERSION_RESOURCE_ID),
                        ),
                        ResourceValue.String("notaninteger".u),
                    ),
                ),
                children = emptyList(),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns PermissionMaxSdkOutOfRange for out-of-range permission maxSdkVersion`() {
        val document = withManifestChildren(
            permissionElement("uses-permission", "android.permission.INTERNET", maxSdkVersion = 0),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.PermissionMaxSdkOutOfRange, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when a permission's name attribute is not a string`() {
        val document = withManifestChildren(
            XmlElement(
                name = XmlExpandedName(None, AsciiNcName.fromUString("uses-permission".u).unwrap()),
                attributes = attributes(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("name".u).unwrap()),
                            Some(NAME_RESOURCE_ID),
                        ),
                        ResourceValue.IntDec(0),
                    ),
                ),
                children = emptyList(),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns PermissionNameTooLong for too long permission name`() {
        val document = withManifestChildren(
            permissionElement("uses-permission", "a".repeat(1025)),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.PermissionNameTooLong, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for non-integer target SDK version`() {
        val document = withUsesSdkAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(
                        Some(ANDROID_NAMESPACE_URI),
                        AsciiNcName.fromUString("targetSdkVersion".u).unwrap(),
                    ),
                    Some(TARGET_SDK_VERSION_RESOURCE_ID),
                ),
                ResourceValue.String("notaninteger".u),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for out-of-range target SDK version`() {
        val document = withUsesSdkAttribute(
            XmlAttribute(
                XmlAttributeId(
                    XmlExpandedName(
                        Some(ANDROID_NAMESPACE_URI),
                        AsciiNcName.fromUString("targetSdkVersion".u).unwrap(),
                    ),
                    Some(TARGET_SDK_VERSION_RESOURCE_ID),
                ),
                ResourceValue.IntDec(0),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for non-integer version code`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("versionCode".u).unwrap(),
                            ),
                            Some(VERSION_CODE_RESOURCE_ID),
                        ),
                        ResourceValue.String("notaninteger".u),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns VersionCodeOutOfRange for out-of-range version code`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("versionCode".u).unwrap(),
                            ),
                            Some(VERSION_CODE_RESOURCE_ID),
                        ),
                        ResourceValue.IntDec(0),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.VersionCodeOutOfRange, error)
    }

    @Test
    fun `fromXmlDocument returns VersionCodeMajorNonZero for non-zero versionCodeMajor`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("versionCodeMajor".u).unwrap(),
                            ),
                            Some(VERSION_CODE_MAJOR_RESOURCE_ID),
                        ),
                        ResourceValue.IntDec(1),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.VersionCodeMajorNonZero, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest for non-integer versionCodeMajor`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("versionCodeMajor".u).unwrap(),
                            ),
                            Some(VERSION_CODE_MAJOR_RESOURCE_ID),
                        ),
                        ResourceValue.String("notaninteger".u),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns VersionNameTooLong for too long version name`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("versionName".u).unwrap(),
                            ),
                            Some(VERSION_NAME_RESOURCE_ID),
                        ),
                        ResourceValue.String("a".repeat(1025).u),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.VersionNameTooLong, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when versionCode attribute lacks its resource ID`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                Some(ANDROID_NAMESPACE_URI),
                                AsciiNcName.fromUString("versionCode".u).unwrap(),
                            ),
                            None,
                        ),
                        ResourceValue.IntDec(1),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when versionCode resource ID carries an unexpected name`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes
                    .without(
                        XmlExpandedName(
                            Some(ANDROID_NAMESPACE_URI),
                            AsciiNcName.fromUString("versionCode".u).unwrap(),
                        ),
                    )
                    .with(
                        XmlAttribute(
                            XmlAttributeId(
                                XmlExpandedName(
                                    Some(ANDROID_NAMESPACE_URI),
                                    AsciiNcName.fromUString("notVersionCode".u).unwrap(),
                                ),
                                Some(VERSION_CODE_RESOURCE_ID),
                            ),
                            ResourceValue.IntDec(1),
                        ),
                    ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when package attribute carries a resource ID`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes.with(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(None, AsciiNcName.fromUString("package".u).unwrap()),
                            Some(ResourceId(0x7F000000u)),
                        ),
                        ResourceValue.String("com.example.app".u),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns MultipleApplicationElements when manifest has multiple application elements`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                children = FULL_VALID_DOCUMENT.root.children.plus(
                    XmlElement(
                        name = XmlExpandedName(None, AsciiNcName.fromUString("application".u).unwrap()),
                        attributes = XmlAttributes.fromList(emptyList()).unwrap(),
                        children = emptyList(),
                    ),
                ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.MultipleApplicationElements, error)
    }

    @Test
    fun `fromXmlDocument returns MultipleUsesSdkElements when manifest has multiple uses-sdk elements`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                children = FULL_VALID_DOCUMENT.root.children.plus(
                    XmlElement(
                        name = XmlExpandedName(None, AsciiNcName.fromUString("uses-sdk".u).unwrap()),
                        attributes = XmlAttributes.fromList(emptyList()).unwrap(),
                        children = emptyList(),
                    )
                )
            )
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.MultipleUsesSdkElements, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when manifest has no application element`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                children = FULL_VALID_DOCUMENT.root.children
                    .filterNot {
                        it.name == XmlExpandedName(None, AsciiNcName.fromUString("application".u).unwrap())
                    },
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when manifest has no package attribute`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes
                    .without(XmlExpandedName(None, AsciiNcName.fromUString("package".u).unwrap())),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when a permission has no name attribute`() {
        val document = withManifestChildren(
            XmlElement(
                name = XmlExpandedName(None, AsciiNcName.fromUString("uses-permission".u).unwrap()),
                attributes = XmlAttributes.fromList(emptyList()).unwrap(),
                children = emptyList(),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns NoVersionCode when manifest has no versionCode attribute`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                attributes = FULL_VALID_DOCUMENT.root.attributes
                    .without(
                        XmlExpandedName(Some(ANDROID_NAMESPACE_URI), AsciiNcName.fromUString("versionCode".u).unwrap()),
                    ),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.Policy.NoVersionCode, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when root element has a namespace`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                name = FULL_VALID_DOCUMENT.root.name.copy(namespaceName = Some(ANDROID_NAMESPACE_URI)),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    @Test
    fun `fromXmlDocument returns InvalidManifest when root element is not 'manifest'`() {
        val document = FULL_VALID_DOCUMENT.copy(
            root = FULL_VALID_DOCUMENT.root.copy(
                name = XmlExpandedName(None, AsciiNcName.fromUString("notmanifest".u).unwrap()),
            ),
        )

        val error = AndroidManifest.fromXmlDocument(document).unwrapErr()

        assertEquals(AndroidManifest.FromXmlError.InvalidManifest, error)
    }

    private companion object {
        private val ANDROID_NAMESPACE_URI =
            Uri.fromUString("http://schemas.android.com/apk/res/android".u).unwrap()

        // Attribute resource IDs from android.R.attr
        private val DEBUGGABLE_RESOURCE_ID = ResourceId(0x0101000fu)
        private val MAX_SDK_VERSION_RESOURCE_ID = ResourceId(0x01010271u)
        private val MIN_SDK_VERSION_RESOURCE_ID = ResourceId(0x0101020cu)
        private val NAME_RESOURCE_ID = ResourceId(0x01010003u)
        private val TARGET_SDK_VERSION_RESOURCE_ID = ResourceId(0x01010270u)
        private val TEST_ONLY_RESOURCE_ID = ResourceId(0x01010272u)
        private val VERSION_CODE_RESOURCE_ID = ResourceId(0x0101021bu)
        private val VERSION_CODE_MAJOR_RESOURCE_ID = ResourceId(0x01010576u)
        private val VERSION_NAME_RESOURCE_ID = ResourceId(0x0101021cu)

        private val FULL_VALID_DOCUMENT = XmlDocument(
            root = XmlElement(
                name = XmlExpandedName(None, AsciiNcName.fromUString("manifest".u).unwrap()),
                attributes = attributes(
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                namespaceName = None,
                                localName = AsciiNcName.fromUString("package".u).unwrap(),
                            ),
                            None,
                        ),
                        ResourceValue.String("com.example.app".u),
                    ),
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                namespaceName = Some(ANDROID_NAMESPACE_URI),
                                localName = AsciiNcName.fromUString("versionCode".u).unwrap(),
                            ),
                            Some(VERSION_CODE_RESOURCE_ID),
                        ),
                        ResourceValue.IntDec(1),
                    ),
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                namespaceName = Some(ANDROID_NAMESPACE_URI),
                                localName = AsciiNcName.fromUString("versionCodeMajor".u).unwrap(),
                            ),
                            Some(VERSION_CODE_MAJOR_RESOURCE_ID),
                        ),
                        ResourceValue.IntDec(0),
                    ),
                    XmlAttribute(
                        XmlAttributeId(
                            XmlExpandedName(
                                namespaceName = Some(ANDROID_NAMESPACE_URI),
                                localName = AsciiNcName.fromUString("versionName".u).unwrap(),
                            ),
                            Some(VERSION_NAME_RESOURCE_ID),
                        ),
                        ResourceValue.String("1.0".u),
                    ),
                ),
                children = listOf(
                    XmlElement(
                        name = XmlExpandedName(None, AsciiNcName.fromUString("application".u).unwrap()),
                        attributes = XmlAttributes.fromList(emptyList()).unwrap(),
                        children = emptyList(),
                    ),
                    XmlElement(
                        name = XmlExpandedName(None, AsciiNcName.fromUString("uses-permission".u).unwrap()),
                        attributes = attributes(
                            XmlAttribute(
                                XmlAttributeId(
                                    XmlExpandedName(
                                        namespaceName = Some(ANDROID_NAMESPACE_URI),
                                        localName = AsciiNcName.fromUString("name".u).unwrap(),
                                    ),
                                    Some(NAME_RESOURCE_ID),
                                ),
                                ResourceValue.String("android.permission.INTERNET".u),
                            ),
                        ),
                        children = emptyList(),
                    ),
                    XmlElement(
                        name = XmlExpandedName(None, AsciiNcName.fromUString("uses-permission-sdk-23".u).unwrap()),
                        attributes = attributes(
                            XmlAttribute(
                                XmlAttributeId(
                                    XmlExpandedName(
                                        namespaceName = Some(ANDROID_NAMESPACE_URI),
                                        localName = AsciiNcName.fromUString("name".u).unwrap(),
                                    ),
                                    Some(NAME_RESOURCE_ID),
                                ),
                                ResourceValue.String("android.permission.READ_EXTERNAL_STORAGE".u),
                            ),
                            XmlAttribute(
                                XmlAttributeId(
                                    XmlExpandedName(
                                        namespaceName = Some(ANDROID_NAMESPACE_URI),
                                        localName = AsciiNcName.fromUString("maxSdkVersion".u).unwrap(),
                                    ),
                                    Some(MAX_SDK_VERSION_RESOURCE_ID),
                                ),
                                ResourceValue.IntDec(32),
                            ),
                        ),
                        children = emptyList(),
                    ),
                    XmlElement(
                        name = XmlExpandedName(None, AsciiNcName.fromUString("uses-sdk".u).unwrap()),
                        attributes = attributes(
                            XmlAttribute(
                                XmlAttributeId(
                                    XmlExpandedName(
                                        namespaceName = Some(ANDROID_NAMESPACE_URI),
                                        localName = AsciiNcName.fromUString("minSdkVersion".u).unwrap(),
                                    ),
                                    Some(MIN_SDK_VERSION_RESOURCE_ID),
                                ),
                                ResourceValue.IntDec(29),
                            ),
                            XmlAttribute(
                                XmlAttributeId(
                                    XmlExpandedName(
                                        namespaceName = Some(ANDROID_NAMESPACE_URI),
                                        localName = AsciiNcName.fromUString("targetSdkVersion".u).unwrap(),
                                    ),
                                    Some(TARGET_SDK_VERSION_RESOURCE_ID),
                                ),
                                ResourceValue.IntDec(37),
                            ),
                        ),
                        children = emptyList(),
                    )
                ),
            ),
        )

        private fun attributes(vararg attributes: XmlAttribute): XmlAttributes {
            return XmlAttributes.fromList(attributes.toList()).unwrap()
        }

        private fun withApplicationAttribute(attribute: XmlAttribute): XmlDocument =
            FULL_VALID_DOCUMENT.copy(
                root = FULL_VALID_DOCUMENT.root.copy(
                    children = FULL_VALID_DOCUMENT.root.children.map {
                        if (it.name == XmlExpandedName(None, AsciiNcName.fromUString("application".u).unwrap())) {
                            it.copy(attributes = it.attributes.with(attribute))
                        } else {
                            it
                        }
                    },
                ),
            )

        private fun withUsesSdkAttribute(attribute: XmlAttribute): XmlDocument =
            FULL_VALID_DOCUMENT.copy(
                root = FULL_VALID_DOCUMENT.root.copy(
                    children = FULL_VALID_DOCUMENT.root.children.map {
                        if (it.name == XmlExpandedName(None, AsciiNcName.fromUString("uses-sdk".u).unwrap())) {
                            it.copy(attributes = it.attributes.with(attribute))
                        } else {
                            it
                        }
                    },
                ),
            )

        private fun withManifestChildren(vararg elements: XmlElement): XmlDocument {
            return FULL_VALID_DOCUMENT.copy(
                root = FULL_VALID_DOCUMENT.root.copy(
                    children = FULL_VALID_DOCUMENT.root.children.plus(elements),
                ),
            )
        }

        private fun permissionElement(
            elementName: String,
            name: String,
            maxSdkVersion: Int? = null,
        ): XmlElement {
            return XmlElement(
                name = XmlExpandedName(None, AsciiNcName.fromUString(elementName.u).unwrap()),
                attributes = XmlAttributes.fromList(
                    buildList {
                        add(
                            XmlAttribute(
                                XmlAttributeId(
                                    XmlExpandedName(
                                        namespaceName = Some(ANDROID_NAMESPACE_URI),
                                        localName = AsciiNcName.fromUString("name".u).unwrap(),
                                    ),
                                    Some(NAME_RESOURCE_ID),
                                ),
                                ResourceValue.String(name.u),
                            ),
                        )
                        if (maxSdkVersion != null) {
                            add(
                                XmlAttribute(
                                    XmlAttributeId(
                                        XmlExpandedName(
                                            Some(ANDROID_NAMESPACE_URI),
                                            AsciiNcName.fromUString("maxSdkVersion".u).unwrap(),
                                        ),
                                        Some(MAX_SDK_VERSION_RESOURCE_ID),
                                    ),
                                    ResourceValue.IntDec(maxSdkVersion),
                                ),
                            )
                        }
                    },
                ).unwrap(),
                children = emptyList(),
            )
        }
    }
}

/**
 * Returns a copy of this attribute collection with [attribute] added, replacing any existing
 * attribute with the same name.
 */
private fun XmlAttributes.with(attribute: XmlAttribute): XmlAttributes {
    return XmlAttributes
        .fromList(asList().filterNot { it.id.name == attribute.id.name }.plus(attribute))
        .unwrap()
}

/**
 * Returns a copy of this attribute collection without the attribute named [name].
 */
private fun XmlAttributes.without(name: XmlExpandedName): XmlAttributes {
    return XmlAttributes.fromList(asList().filterNot { it.id.name == name }).unwrap()
}
