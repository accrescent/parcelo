package app.accrescent.parcelo.validation

data class AndroidManifest(
    val `package`: String,
    val versionCode: Int,
    val versionName: String?,
    val split: String?,
    val application: Application,
)

data class Application(val debuggable: Boolean?)
