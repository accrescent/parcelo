package app.accrescent.parcelo.apksparser

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

public class AndroidManifest private constructor(
    public val `package`: AppId,
    public val versionCode: Int,
    public val versionName: String?,
    public val split: String?,
    public val application: Application,
    @JacksonXmlProperty(localName = "uses-sdk")
    public val usesSdk: UsesSdk?,
) {
    /**
     * @throws IllegalArgumentException the app ID is not well-formed
     */
    @JsonCreator
    private constructor(
        `package`: String,
        versionCode: Int,
        versionName: String?,
        split: String?,
        application: Application,
        usesSdk: UsesSdk?
    ) : this(
        AppId.parseFromString(`package`) ?: throw IllegalArgumentException(),
        versionCode,
        versionName,
        split,
        application,
        usesSdk,
    )

    @JacksonXmlProperty(localName = "uses-permission")
    public var usesPermissions: List<UsesPermission>? = null
        // Jackson workaround to prevent permission review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field = field.orEmpty() + value.orEmpty()
        }
}

public class Action private constructor(public val name: String)

public class Application private constructor(
    public val debuggable: Boolean?,
    public val testOnly: Boolean?,
) {
    @JacksonXmlProperty(localName = "service")
    public var services: List<Service>? = null
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field = field.orEmpty() + value.orEmpty()
        }
}

public class IntentFilter private constructor() {
    @JacksonXmlProperty(localName = "action")
    public var actions: List<Action> = emptyList()
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field += value
        }
}

public class Service private constructor() {
    @JacksonXmlProperty(localName = "intent-filter")
    public var intentFilters: List<IntentFilter>? = null
        // Jackson workaround to prevent review bypasses. See
        // https://github.com/FasterXML/jackson-dataformat-xml/issues/275 for more information.
        private set(value) {
            field = field.orEmpty() + value.orEmpty()
        }
}

public class UsesPermission private constructor(
    public val name: String,
    public val maxSdkVersion: String?,
)

public class UsesSdk private constructor(
    public val minSdkVersion: Int = 1,
    public val targetSdkVersion: Int?,
)
