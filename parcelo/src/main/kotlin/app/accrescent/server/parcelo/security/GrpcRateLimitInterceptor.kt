// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.api.error.CommonApiError
import app.accrescent.server.parcelo.api.error.CommonErrorReason
import app.accrescent.server.parcelo.config.ParceloConfig
import com.google.protobuf.duration
import com.google.rpc.RetryInfo
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.quarkus.logging.Log
import io.vertx.core.Vertx
import io.vertx.grpc.BlockingServerInterceptor
import jakarta.enterprise.context.ApplicationScoped
import java.net.InetAddress
import java.net.InetSocketAddress
import com.google.protobuf.Any as AnyProto

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

        private val noAddressError = CommonApiError(
            CommonErrorReason.INTERNAL,
            "no remote address found",
        )
            .toStatusRuntimeException()
        private val addressNotIpError = CommonApiError(
            CommonErrorReason.INTERNAL,
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

        try {
            return when (val result = rateLimitService.tryRequest(principal, apiCategory)) {
                RateLimitResult.Allowed -> next.startCall(call, headers)
                is RateLimitResult.LimitExceeded -> {
                    val delay = duration {
                        seconds = result.retryDelay.seconds
                        nanos = result.retryDelay.nano
                    }
                    val error = CommonApiError(
                        CommonErrorReason.RATE_LIMIT_EXCEEDED,
                        "rate limit exceeded",
                        listOf(AnyProto.pack(RetryInfo.newBuilder().setRetryDelay(delay).build())),
                    )
                        .toStatusRuntimeException()

                    call.close(error.status, error.trailers ?: Metadata())
                    object : ServerCall.Listener<ReqT>() {}
                }
            }
        } catch (t: Throwable) {
            Log.error("TODO rate limit error", t)
            throw t
        }
    }
}
