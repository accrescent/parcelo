package app.accrescent.parcelo.validation

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class AndroidManifest(
    val `package`: String,
    val versionCode: Int,
    val versionName: String?,
    val split: String?,
    val application: Application,
    @JacksonXmlProperty(localName = "uses-sdk")
    val usesSdk: UsesSdk?,
) {
    @JacksonXmlProperty(localName = "uses-permission")
    var usesPermissions: List<UsesPermission>? = null
        // Jackson workaround to prevent permission review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

data class Action(val name: String)

data class Application(
    val debuggable: Boolean?,
) {
    @JacksonXmlProperty(localName = "service")
    var services: List<Service>? = null
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

class IntentFilter {
    @JacksonXmlProperty(localName = "action")
    var actions: List<Action> = emptyList()
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        set(value) {
            field += value
        }
}

class Service {
    @JacksonXmlProperty(localName = "intent-filter")
    var intentFilters: List<IntentFilter>? = null
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

data class UsesPermission(val name: String, val maxSdkVersion: String?)

data class UsesSdk(val minSdkVersion: Int = 1, val targetSdkVersion: Int?)
