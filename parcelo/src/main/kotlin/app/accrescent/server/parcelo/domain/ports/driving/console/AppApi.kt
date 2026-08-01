// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

import arrow.core.Either

interface AppApi {
    fun getApp(callerUserId: String, request: GetAppRequest): Either<GetAppError, GetAppResponse>
    fun updateApp(callerUserId: String, request: UpdateAppRequest): Either<UpdateAppError, Unit>
}
