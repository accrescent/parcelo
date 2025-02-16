// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.validation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The minimum target SDK accepted for both drafts and updates
 */
const val MIN_TARGET_SDK = 34

/**
 * The blacklist of permissions which, when requested by an update for the first time, trigger a
 * review which must pass before the update can be published.
 */
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

/**
 * Similar to the permission review blacklist, this list contains service intent filter actions
 * which trigger a review when requested for the first time by an update.
 */
val SERVICE_INTENT_FILTER_REVIEW_BLACKLIST = setOf(
    "android.accessibilityservice.AccessibilityService",
    "android.net.VpnService",
    "android.view.InputMethod"
)

/**
 * A convenience union of the permission review blacklist and service intent filter action blacklist
 */
val REVIEW_ISSUE_BLACKLIST =
    PERMISSION_REVIEW_BLACKLIST union SERVICE_INTENT_FILTER_REVIEW_BLACKLIST

/**
 * The possible results of a review
 */
@Serializable
enum class ReviewResult {
    /**
     * The review expresses approval
     */
    @SerialName("approved")
    APPROVED,

    /**
     * The review expresses rejection
     */
    @SerialName("rejected")
    REJECTED,
}

/**
 * A review object
 */
@Serializable
data class ReviewRequest(
    /**
     * The result of the review
     */
    val result: ReviewResult,
    /**
     * A list of reasons for rejection
     *
     * Present if and only if [result] is [ReviewResult.REJECTED].
     */
    val reasons: List<String>?,
    /**
     * Additional notes pertaining to the review
     *
     * This field is general-purpose, but is intended for supplying helpful information to the user
     * requesting review. For example, it can be used for detailing reasons for rejection or adding
     * tips for relevant upcoming policy changes.
     */
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
