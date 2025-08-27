// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

import java.io.InputStream
import java.util.UUID

/**
 * An abstraction over the implementation which publishes app data, i.e., makes it available for
 * download to the client
 */
interface PublishService {
    /**
     * Publishes a draft
     */
    suspend fun publishDraft(
        apkSet: InputStream,
        icon: InputStream,
        appName: String,
        shortDescription: String,
    )

    /**
     * Publishes an app update
     */
    suspend fun publishUpdate(
        apkSet: InputStream,
        updateId: UUID,
        currentIcon: InputStream,
        currentAppName: String,
        currentShortDescription: String,
    )

    /**
     * Publishes an edit
     */
    suspend fun publishEdit(
        appId: String,
        editId: UUID,
        currentApkSet: InputStream,
        currentIcon: InputStream,
        shortDescription: String?,
    )
}
