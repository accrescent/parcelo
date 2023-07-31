package app.accrescent.parcelo.repository.data.net

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class RepoData(
    val version: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("apk_groups")
    val apkGroups: SortedMap<Int, ApkGroup>,
)

@Serializable
data class ApkGroup(
    @SerialName("base_split_ids")
    @Contextual
    val baseSplitId: UUID,
    @SerialName("abi_split_ids")
    val abiSplitIds: Map<String, @Contextual UUID>,
    @SerialName("lang_split_ids")
    val langSplitIds: Map<String, @Contextual UUID>,
    @SerialName("density_split_ids")
    val densitySplitIDs: Map<String, @Contextual UUID>,
)