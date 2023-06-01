package app.accrescent.parcelo.apksparser

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

public data class AndroidManifest(
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
        private set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

public data class Action(val name: String)

public data class Application(
    val debuggable: Boolean?,
    val testOnly: Boolean?,
) {
    @JacksonXmlProperty(localName = "service")
    var services: List<Service>? = null
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

public class IntentFilter {
    @JacksonXmlProperty(localName = "action")
    public var actions: List<Action> = emptyList()
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field += value
        }
}

public class Service {
    @JacksonXmlProperty(localName = "intent-filter")
    public var intentFilters: List<IntentFilter>? = null
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field = (field ?: emptyList()) + (value ?: emptyList())
        }
}

public data class UsesPermission(val name: String, val maxSdkVersion: String?)

public data class UsesSdk(val minSdkVersion: Int = 1, val targetSdkVersion: Int?)
