package app.accrescent.parcelo.console.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Update(
    val id: String,
    @SerialName("app_id")
    val appId: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("version_name")
    val versionName: String,
    @SerialName("creation_time")
    val creationTime: Long,
    @SerialName("requires_review")
    val requiresReview: Boolean,
)
