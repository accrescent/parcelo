package app.accrescent.parcelo.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Update(
    val id: String,
    @SerialName("app_id")
    val appId: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("requires_review")
    val requiresReview: Boolean,
)
