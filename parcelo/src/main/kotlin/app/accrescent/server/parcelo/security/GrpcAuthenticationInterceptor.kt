// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.api.error.CommonApiError
import app.accrescent.server.parcelo.api.error.CommonErrorReason
import app.accrescent.server.parcelo.data.User
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.quarkus.logging.Log
import io.quarkus.oidc.IdToken
import io.vertx.core.Vertx
import io.vertx.grpc.BlockingServerInterceptor
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.spi.Prioritized
import org.eclipse.microprofile.jwt.JsonWebToken

@ApplicationScoped
class GrpcAuthenticationInterceptor(
    @IdToken
    private val idToken: JsonWebToken,
    private val vertx: Vertx,
) : ServerInterceptor by BlockingServerInterceptor.wrap(
    vertx,
    GrpcAuthenticationInterceptorImpl(idToken),
), Prioritized {
    // The authentication interceptor should run before all other interceptors if present so that
    // the user ID it injects into the gRPC context is available to other interceptors, e.g., the
    // rate limiting interceptor
    override fun getPriority(): Int {
        return 1
    }
}

private class GrpcAuthenticationInterceptorImpl(
    private val idToken: JsonWebToken,
) : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        if (idToken.rawToken == null) {
            call.close(noCredentialsError.status, noCredentialsError.trailers ?: Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        val userId = try {
            User.findIdByOidcId(idToken.issuer, idToken.subject)?.id ?: run {
                call.close(notRegisteredError.status, notRegisteredError.trailers ?: Metadata())
                return object : ServerCall.Listener<ReqT>() {}
            }
        } catch (t: Throwable) {
            Log.error("TODO User.findIdByOidcId error", t)
            throw t
        }
        val context = Context.current().withValue(AuthnContextKey.USER_ID, userId)

        return Contexts.interceptCall(context, call, headers, next)
    }

    private companion object {
        private val noCredentialsError = CommonApiError(
            CommonErrorReason.NO_CREDENTIALS,
            "no authentication credentials provided",
        )
            .toStatusRuntimeException()

        private val notRegisteredError = CommonApiError(
            CommonErrorReason.NOT_REGISTERED,
            "the authenticated user is not registered",
        )
            .toStatusRuntimeException()
    }
}
