// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.parsers

import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.UnmarshalException
import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlRootElement
import java.io.StringReader

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

// Imposed by us to set a reasonable limit
private const val APP_ID_MAX_LENGTH = 128

// https://developer.android.com/guide/topics/manifest/application-element#debug
private const val APPLICATION_DEBUGGABLE_DEFAULT = false

// https://developer.android.com/guide/topics/manifest/application-element#testOnly
private const val APPLICATION_TEST_ONLY_DEFAULT = false

// https://developer.android.com/guide/topics/manifest/uses-sdk-element#min
private const val MIN_SDK_VERSION_DEFAULT = 1

// Version codes of < 1 are not installable on Android devices
private const val VERSION_CODE_MIN_VALUE = 1

@RegisterForReflection
data class AndroidManifest(
    val `package`: String,
    val versionCode: Int,
    val versionName: String?,
    val split: String?,
    val application: Application,
    val usesSdk: UsesSdk?,
    val usesPermissions: List<UsesPermission>,
) {
    @XmlRootElement(name = "manifest")
    @XmlAccessorType(XmlAccessType.FIELD)
    private data class RawManifest(
        @XmlAttribute
        var `package`: String? = null,

        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var versionCode: Int? = null,

        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var versionName: String? = null,

        @XmlAttribute
        var split: String? = null,

        @XmlElement
        var application: RawApplication? = null,

        @XmlElement(name = "uses-sdk")
        var usesSdk: RawUsesSdk? = null,

        @XmlElement(name = "uses-permission")
        var usesPermissions: List<RawUsesPermission>? = null,
    ) {
        fun toAndroidManifest(): AndroidManifest? {
            return AndroidManifest(
                `package` = `package`
                    ?.takeIf { it.length <= APP_ID_MAX_LENGTH && appIdRegex.matches(it) }
                    ?: return null,
                versionCode = versionCode
                    ?.takeIf { it >= VERSION_CODE_MIN_VALUE }
                    ?: return null,
                versionName = versionName,
                split = split,
                application = application
                    ?.let {
                        Application(
                            debuggable = it.debuggable ?: APPLICATION_DEBUGGABLE_DEFAULT,
                            testOnly = it.testOnly ?: APPLICATION_TEST_ONLY_DEFAULT,
                        )
                    }
                    ?: return null,
                usesSdk = usesSdk
                    ?.let {
                        val minSdkVersion = it.minSdkVersion ?: MIN_SDK_VERSION_DEFAULT
                        UsesSdk(
                            minSdkVersion = minSdkVersion,
                            // Target SDK defaults to min SDK when not set according to
                            // https://developer.android.com/guide/topics/manifest/uses-sdk-element#target
                            targetSdkVersion = it.targetSdkVersion ?: minSdkVersion
                        )
                    },
                usesPermissions = usesPermissions
                    ?.map {
                        UsesPermission(
                            name = it.name ?: return null,
                            maxSdkVersion = it.maxSdkVersion,
                        )
                    }
                    ?: emptyList(),
            )
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private data class RawApplication(
        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var debuggable: Boolean? = null,

        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var testOnly: Boolean? = null,
    )

    @XmlAccessorType(XmlAccessType.FIELD)
    private data class RawUsesSdk(
        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var minSdkVersion: Int? = null,

        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var targetSdkVersion: Int? = null,
    )

    @XmlAccessorType(XmlAccessType.FIELD)
    private data class RawUsesPermission(
        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var name: String? = null,

        @XmlAttribute(namespace = ANDROID_NAMESPACE)
        var maxSdkVersion: Int? = null,
    )

    companion object {
        private val appIdRegex = Regex("""^([a-zA-Z][a-zA-Z0-9_]*\.)+[a-zA-Z][a-zA-Z0-9_]*$""")

        fun parse(xml: String): AndroidManifest? {
            val manifest = try {
                StringReader(xml).use {
                    JAXBContext
                        .newInstance(RawManifest::class.java)
                        .createUnmarshaller()
                        .unmarshal(it) as? RawManifest
                }
            } catch (_: UnmarshalException) {
                return null
            }

            return manifest?.toAndroidManifest()
        }
    }
}

data class Application(
    val debuggable: Boolean,
    val testOnly: Boolean,
)

data class UsesSdk(
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
)

data class UsesPermission(
    val name: String,
    val maxSdkVersion: Int?,
)
