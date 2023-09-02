// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.validation

import com.github.zafarkhaja.semver.Version
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MIN_TARGET_SDK = 31

/**
 * The minimum acceptable bundletool version used to generate the APK set. This version is taken
 * from a recent Android Studio release.
 */
val MIN_BUNDLETOOL_VERSION: Version = Version.Builder("1.11.4").build()

val PERMISSION_REVIEW_BLACKLIST = setOf(
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.CAMERA",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.NEARBY_WIFI_DEVICES",
    "android.permission.PROCESS_OUTGOING_CALLS",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_MMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.RECEIVE_WAP_PUSH",
    "android.permission.RECORD_AUDIO",
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SEND_SMS",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.WRITE_CONTACTS",
    "android.permission.SYSTEM_ALERT_WINDOW",
)

val SERVICE_INTENT_FILTER_REVIEW_BLACKLIST = setOf(
    "android.accessibilityservice.AccessibilityService",
    "android.net.VpnService",
    "android.view.InputMethod"
)

val REVIEW_ISSUE_BLACKLIST =
    PERMISSION_REVIEW_BLACKLIST union SERVICE_INTENT_FILTER_REVIEW_BLACKLIST

@Serializable
enum class ReviewResult {
    @SerialName("approved")
    APPROVED,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
data class ReviewRequest(
    val result: ReviewResult,
    val reasons: List<String>?,
    @SerialName("additional_notes")
    val additionalNotes: String?,
) {
    // FIXME(#114): Handle this validation automatically via kotlinx.serialization instead
    init {
        require(
            result == ReviewResult.APPROVED && reasons == null ||
                result == ReviewResult.REJECTED && reasons != null
        )
    }
}
