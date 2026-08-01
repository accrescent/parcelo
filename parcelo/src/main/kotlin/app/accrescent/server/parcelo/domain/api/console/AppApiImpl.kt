// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.api.console

import app.accrescent.server.parcelo.core.bindMapLeft
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.datastore.HasPermissionRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.App
import app.accrescent.server.parcelo.domain.ports.driving.console.AppApi
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppError
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.InsufficientPermissionError
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppError
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.toServerError
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

class AppApiImpl(private val dataStore: DataStore) : AppApi {
    override fun getApp(
        callerUserId: String,
        request: GetAppRequest,
    ): Either<GetAppError, GetAppResponse> = either {
        val app = dataStore.runTxWithRetry { tx ->
            val permitted = tx.authz
                .hasPermission(HasPermissionRequest.ViewApp(request.appId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(permitted) { InsufficientPermissionError }

            // App is guaranteed to exist since permission is granted
            tx.apps.requireById(request.appId).bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()

        GetAppResponse(
            app = App(
                id = app.id,
                defaultAppListingId = app.defaultAppListingId,
                publiclyListed = app.publiclyListed,
            )
        )
    }

    override fun updateApp(
        callerUserId: String,
        request: UpdateAppRequest,
    ): Either<UpdateAppError, Unit> = either {
        dataStore.runTxWithRetry { tx ->
            val permitted = tx.authz
                .hasPermission(HasPermissionRequest.UpdateApp(request.appId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(permitted) { InsufficientPermissionError }

            // App is guaranteed to exist since permission was granted, so no need to check for its
            // existence before attempting to update it
            tx.apps
                .updatePubliclyListed(request.appId, request.publiclyListed)
                .bindMapLeft(::toServerError)

        }
            .bindMapLeft(::toServerError)
            .bind()
    }
}
