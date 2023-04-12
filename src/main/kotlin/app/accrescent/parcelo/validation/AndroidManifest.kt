package app.accrescent.parcelo.validation

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class AndroidManifest(
    val `package`: String,
    val versionCode: Int,
    val versionName: String?,
    val split: String?,
    val application: Application,
    @JacksonXmlProperty(localName = "uses-permission")
    val usesPermissions: List<UsesPermission>?,
)

data class Application(val debuggable: Boolean?)

data class UsesPermission(val name: String, val maxSdkVersion: String?)
