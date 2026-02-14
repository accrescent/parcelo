// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import java.net.URL
import java.util.concurrent.TimeUnit

// 1 GiB
private const val MAX_APK_SET_SIZE_BYTES = 1073741824L

// 1 MiB
private const val MAX_ICON_SIZE_BYTES = 1048576L

private const val DOWNLOAD_URL_EXPIRATION_SECONDS = 30L
private const val UPLOAD_URL_EXPIRATION_SECONDS = 30L

// https://docs.cloud.google.com/storage/docs/xml-api/reference-headers#xgoogcontentlengthrange
private const val CONTENT_LENGTH_RANGE_HEADER_NAME = "X-Goog-Content-Length-Range"

enum class UploadType(val maxSizeBytes: Long) {
    APK_SET(MAX_APK_SET_SIZE_BYTES),
    ICON(MAX_ICON_SIZE_BYTES),
}

fun Storage.signDownloadUrl(blobInfo: BlobInfo): URL {
    return signUrl(
        blobInfo,
        DOWNLOAD_URL_EXPIRATION_SECONDS,
        TimeUnit.SECONDS,
        Storage.SignUrlOption.withV4Signature(),
    )
}

fun Storage.signUploadUrl(blobInfo: BlobInfo, uploadType: UploadType): URL {
    return signUrl(
        blobInfo,
        UPLOAD_URL_EXPIRATION_SECONDS,
        TimeUnit.SECONDS,
        Storage.SignUrlOption.withV4Signature(),
        Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
        Storage.SignUrlOption.withExtHeaders(
            mapOf(CONTENT_LENGTH_RANGE_HEADER_NAME to "0,${uploadType.maxSizeBytes}")
        ),
    )
}
