// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.vertx.core.Vertx
import io.vertx.grpc.BlockingServerInterceptor
import jakarta.enterprise.context.ApplicationScoped
import java.net.InetAddress
import java.net.InetSocketAddress

@ApplicationScoped
class GrpcRateLimitInterceptor(
    private val config: ParceloConfig,
    private val rateLimitService: RateLimitService,
    private val vertx: Vertx,
) : ServerInterceptor by BlockingServerInterceptor
    .wrap(vertx, GrpcRateLimitInterceptorImpl(config, rateLimitService))

private class GrpcRateLimitInterceptorImpl(
    private val config: ParceloConfig,
    private val rateLimitService: RateLimitService,
) : ServerInterceptor {
    private companion object {
        private val CLOUDFLARE_CONNECTING_IP_KEY =
            Metadata.Key.of("cf-connecting-ip", Metadata.ASCII_STRING_MARSHALLER)
        private val UPLOAD_APIS_PATTERN = Regex(""".*/Create.*UploadOperation""")

        private val rateLimitError = ConsoleApiError(
            ErrorReason.ERROR_REASON_RATE_LIMIT_EXCEEDED,
            "rate limit exceeded",
        )
            .toStatusRuntimeException()
        private val noAddressError = ConsoleApiError(
            ErrorReason.ERROR_REASON_INTERNAL,
            "no remote address found",
        )
            .toStatusRuntimeException()
        private val addressNotIpError = ConsoleApiError(
            ErrorReason.ERROR_REASON_INTERNAL,
            "client address is not an IP address",
        )
            .toStatusRuntimeException()
    }

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val userId = AuthnContextKey.USER_ID.get()

        val principal = if (userId == null) {
            when (config.rateLimiting().ipSource()) {
                ParceloConfig.IpSource.CONNECTION -> {
                    val clientAddress = call.attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR) ?: run {
                        call.close(noAddressError.status, noAddressError.trailers ?: Metadata())
                        return object : ServerCall.Listener<ReqT>() {}
                    }
                    if (clientAddress !is InetSocketAddress) {
                        call.close(
                            addressNotIpError.status,
                            addressNotIpError.trailers ?: Metadata(),
                        )
                        return object : ServerCall.Listener<ReqT>() {}
                    }

                    Principal.IpAddress(clientAddress.address)
                }

                ParceloConfig.IpSource.CLOUDFLARE -> {
                    val connectingIp = headers.get(CLOUDFLARE_CONNECTING_IP_KEY) ?: run {
                        call.close(noAddressError.status, noAddressError.trailers ?: Metadata())
                        return object : ServerCall.Listener<ReqT>() {}
                    }
                    val address = InetAddress.getByName(connectingIp)

                    Principal.IpAddress(address)
                }
            }
        } else {
            Principal.User(userId)
        }

        val methodId = call.methodDescriptor.fullMethodName
        val apiCategory = if (UPLOAD_APIS_PATTERN.matches(methodId)) {
            ApiCategory.UPLOAD_APIS
        } else {
            null
        }

        if (!rateLimitService.tryRequest(principal, apiCategory)) {
            call.close(rateLimitError.status, rateLimitError.trailers ?: Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }

        return next.startCall(call, headers)
    }
}
