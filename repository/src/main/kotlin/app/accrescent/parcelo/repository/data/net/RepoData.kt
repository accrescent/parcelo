package app.accrescent.parcelo.repository.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepoData(
    val version: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("base_splits")
    val bases: Set<String>,
    @SerialName("abi_splits")
    val abiSplits: Set<String>,
    @SerialName("density_splits")
    val densitySplits: Set<String>,
    @SerialName("lang_splits")
    val langSplits: Set<String>,
)
