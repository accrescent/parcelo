// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.util.sha256Hash
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.vertx.core.Vertx
import io.vertx.grpc.BlockingServerInterceptor
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.spi.Prioritized

@ApplicationScoped
class GrpcAuthenticationInterceptor(
    private val vertx: Vertx,
) : ServerInterceptor by BlockingServerInterceptor.wrap(vertx, GrpcAuthenticationInterceptorImpl),
    Prioritized {
    // The authentication interceptor should run before all other interceptors if present so that
    // the user ID it injects into the gRPC context is available to other interceptors, e.g., the
    // rate limiting interceptor
    override fun getPriority(): Int {
        return 1
    }
}

private object GrpcAuthenticationInterceptorImpl : ServerInterceptor {
    private val AUTHORIZATION_HEADER_KEY =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val rawHeaderValue = headers.get(AUTHORIZATION_HEADER_KEY)
        if (rawHeaderValue?.startsWith("Bearer ") != true) {
            call.close(Status.UNAUTHENTICATED, Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        val rawApiKey = rawHeaderValue.removePrefix("Bearer ")
        val hashedApiKey = sha256Hash(rawApiKey.toByteArray())

        val userId = User.findIdByApiKeyHash(hashedApiKey)?.id ?: run {
            call.close(Status.UNAUTHENTICATED, Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        val context = Context.current().withValue(AuthnContextKey.USER_ID, userId)

        return Contexts.interceptCall(context, call, headers, next)
    }
}
