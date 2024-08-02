// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

import java.io.InputStream

/**
 * An abstraction over the implementation which publishes app data, i.e., makes it available for
 * download to the client
 */
interface PublishService {
    /**
     * Publishes a draft
     *
     * @return the repository metadata
     */
    suspend fun publishDraft(
        apkSet: InputStream,
        icon: InputStream,
        shortDescription: String,
    ): ByteArray

    /**
     * Publishes an app update
     *
     * @return the updated repository metadata
     */
    suspend fun publishUpdate(apkSet: InputStream, appId: String): ByteArray

    /**
     * Publishes an edit
     *
     * @return the updated repository metadata
     */
    suspend fun publishEdit(appId: String, shortDescription: String?): ByteArray
}
