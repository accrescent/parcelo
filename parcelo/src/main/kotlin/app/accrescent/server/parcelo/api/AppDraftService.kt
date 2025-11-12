// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.AppDraftServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.createAppDraftResponse
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import io.grpc.stub.StreamObserver
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
class AppDraftService : AppDraftServiceGrpc.AppDraftServiceImplBase() {
    override fun createAppDraft(
        request: CreateAppDraftRequest,
        responseObserver: StreamObserver<CreateAppDraftResponse>,
    ) {
        val userId = AuthnContextKey.USER_ID.get()

        val response = createAppDraftResponse {
            id = "${userId.provider}|${userId.scopedValue}"
        }
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}
