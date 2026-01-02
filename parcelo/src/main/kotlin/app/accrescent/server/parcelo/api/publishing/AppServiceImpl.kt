// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.AppService
import app.accrescent.appstore.publish.v1alpha1.GetAppRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppResponse
import app.accrescent.appstore.publish.v1alpha1.app
import app.accrescent.appstore.publish.v1alpha1.getAppResponse
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
class AppServiceImpl : AppService {
    @Transactional
    override fun getApp(request: GetAppRequest): Uni<GetAppResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val app = App.findById(request.appId)
        val canView = PermissionService.userCanViewApp(userId, request.appId)
        if (!canView || app == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app with ID \"${request.appId}\" not found")
                .asRuntimeException()
        }

        val response = getAppResponse {
            this.app = app {
                id = app.id
            }
        }

        return Uni.createFrom().item { response }
    }
}
