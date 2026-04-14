// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.review

import app.accrescent.server.parcelo.data.AppEdit
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

private const val ANDROID_PERMISSION_PREFIX = "android.permission."

@ApplicationScoped
class ReviewService {
    @Transactional(Transactional.TxType.MANDATORY)
    fun appEditRequiresReview(appEdit: AppEdit): Boolean {
        return appEditListingChangesRequireReview(appEdit)
                || appEditPermissionChangesRequireReview(appEdit)
    }

    private fun appEditListingChangesRequireReview(appEdit: AppEdit): Boolean {
        val appListings = appEdit.app.listings.associateBy { it.language }
        val editListings = appEdit.listings
        val descriptionChangesRequiringReview = editListings
            .filter { listing ->
                val oldListing = appListings[listing.language]
                val isNewOrModified = oldListing == null
                        || oldListing.name != listing.name
                        || oldListing.shortDescription != listing.shortDescription
                        || oldListing.iconImageId != listing.iconImageId

                isNewOrModified
            }

        return descriptionChangesRequiringReview.isNotEmpty()
    }

    private fun appEditPermissionChangesRequireReview(appEdit: AppEdit): Boolean {
        val appPermissions = appEdit.app.appPackage.permissions.associateBy { it.name }
        val editPermissions = appEdit.appPackage.permissions
        val permissionChangesRequiringReview = editPermissions
            .filter { permission ->
                val isAndroidPermission = permission.name.startsWith(ANDROID_PERMISSION_PREFIX)
                val isAllowedWithoutReview = PERMISSIONS_ALLOWED_WITHOUT_REVIEW
                    .contains(permission.name)
                val oldPermission = appPermissions[permission.name]
                val isMorePermissive = oldPermission == null || run {
                    val oldMaxSdkVersion = oldPermission.maxSdkVersion
                    val maxSdkVersion = permission.maxSdkVersion

                    oldMaxSdkVersion != null
                            && (maxSdkVersion == null || maxSdkVersion > oldMaxSdkVersion)
                }

                isAndroidPermission && isMorePermissive && !isAllowedWithoutReview
            }

        return permissionChangesRequiringReview.isNotEmpty()
    }

    private companion object {
        private val PERMISSIONS_ALLOWED_WITHOUT_REVIEW = setOf(
            // Basic network functionality is not considered review-worthy for our purposes.
            "android.permission.ACCESS_NETWORK_STATE",
            // Not security sensitive since it applied to only the currently focused application.
            "android.permission.CAPTURE_KEYBOARD",
            // Foreground services are not inherently security-sensitive, though they do have an
            // effect on battery life.
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_CAMERA",
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.FOREGROUND_SERVICE_HEALTH",
            "android.permission.FOREGROUND_SERVICE_LOCATION",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            // Basic network functionality is not considered review-worthy for our purposes.
            "android.permission.INTERNET",
            // According to Android documentation, "holding this permission does not have any
            // security implications".
            //
            // https://developer.android.com/reference/android/Manifest.permission#RECEIVE_BOOT_COMPLETED
            "android.permission.RECEIVE_BOOT_COMPLETED",
            // Android doesn't expose biometric authentication data directly to applications.
            "android.permission.USE_BIOMETRIC",
            // Deprecated practical equivalent to a subset of USE_BIOMETRIC.
            "android.permission.USE_FINGERPRINT",
            // Haptics aren't worth reviewing for our purposes.
            "android.permission.VIBRATE",
        )
    }
}
