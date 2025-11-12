// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.model.UserId
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
import java.security.MessageDigest

@ApplicationScoped
class GrpcAuthenticationInterceptor(
    private val vertx: Vertx,
) : ServerInterceptor by BlockingServerInterceptor.wrap(vertx, GrpcAuthenticationInterceptorImpl)

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

        val user = User.findByApiKeyHash(hashedApiKey) ?: run {
            call.close(Status.UNAUTHENTICATED, Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }

        val context = Context.current().withValue(
            AuthnContextKey.USER_ID,
            UserId(provider = user.identityProvider, scopedValue = user.scopedUserId),
        )
        return Contexts.interceptCall(context, call, headers, next)
    }

    private fun sha256Hash(input: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(input).toHexString()
    }
}
