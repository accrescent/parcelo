package app.accrescent.parcelo.validation

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class AndroidManifest(
    val `package`: String,
    val versionCode: Int,
    val versionName: String?,
    val split: String?,
    val application: Application,
) {
    @JacksonXmlProperty(localName = "uses-permission")
    var usesPermissions: List<UsesPermission>? = null
        // Jackson workaround to prevent permission review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

data class Application(val debuggable: Boolean?)

data class UsesPermission(val name: String, val maxSdkVersion: String?)
