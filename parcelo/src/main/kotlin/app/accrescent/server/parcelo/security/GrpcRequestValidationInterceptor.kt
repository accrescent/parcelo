// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.api.error.CommonApiError
import app.accrescent.server.parcelo.api.error.CommonErrorReason
import build.buf.protovalidate.Validator
import build.buf.protovalidate.ValidatorFactory
import build.buf.protovalidate.exceptions.ValidationException
import com.google.protobuf.Message
import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GrpcRequestValidationInterceptor : ServerInterceptor {
    private val validator = ValidatorFactory
        .newBuilder()
        .build()

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val listener = next.startCall(call, headers)
        return GrpcRequestValidationServerCallListener(listener, validator)
    }
}

private class GrpcRequestValidationServerCallListener<ReqT : Any>(
    delegate: ServerCall.Listener<ReqT>,
    private val validator: Validator,
) : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
    override fun onMessage(message: ReqT) {
        if (message !is Message) {
            throw IllegalArgumentException(
                "expected message type to be ${Message::class.qualifiedName} " +
                        "but got ${message::class.qualifiedName}"
            )
        }

        val validationResult = try {
            validator.validate(message)
        } catch (e: ValidationException) {
            throw CommonApiError(
                CommonErrorReason.INTERNAL,
                e.message.toString(),
            )
                .toStatusRuntimeException()
        }

        if (validationResult.isSuccess) {
            super.onMessage(message)
        } else {
            throw CommonApiError(
                CommonErrorReason.INVALID_REQUEST,
                validationResult.toString(),
            )
                .toStatusRuntimeException()
        }
    }
}
