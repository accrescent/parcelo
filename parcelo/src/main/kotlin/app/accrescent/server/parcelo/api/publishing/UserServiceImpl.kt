// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.GetSelfRequest
import app.accrescent.appstore.publish.v1alpha1.GetSelfResponse
import app.accrescent.appstore.publish.v1alpha1.UserService
import app.accrescent.appstore.publish.v1alpha1.getSelfResponse
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class UserServiceImpl : UserService {
    @Transactional
    override fun getSelf(request: GetSelfRequest): Uni<GetSelfResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val response = getSelfResponse { this.userId = userId.toString() }

        return Uni.createFrom().item { response }
    }
}
