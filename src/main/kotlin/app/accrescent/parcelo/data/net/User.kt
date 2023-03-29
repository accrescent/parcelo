package app.accrescent.parcelo.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: String, @SerialName("gh_id") val githubUserId: Long)
