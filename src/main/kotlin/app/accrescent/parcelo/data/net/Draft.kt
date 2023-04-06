package app.accrescent.parcelo.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Draft(
    val id: String,
    @SerialName("app_id")
    val appId: String,
    val label: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("version_name")
    val versionName: String,
    @SerialName("icon_hash")
    val iconHash: String,
    val submitted: Boolean,
    val approved: Boolean,
)
