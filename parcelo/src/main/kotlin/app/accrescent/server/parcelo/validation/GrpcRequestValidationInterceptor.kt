// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.validation

import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftPackageUploadInfoRequest
import build.buf.protovalidate.Validator
import build.buf.protovalidate.ValidatorFactory
import build.buf.protovalidate.exceptions.ValidationException
import com.google.protobuf.Message
import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GrpcRequestValidationInterceptor : ServerInterceptor {
    private val validator = ValidatorFactory
        .newBuilder()
        .buildWithDescriptors(
            listOf(
                CreateAppDraftRequest.getDescriptor(),
                DeleteAppDraftRequest.getDescriptor(),
                GetAppDraftPackageUploadInfoRequest.getDescriptor()
            ),
            true,
        )

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
            throw Status.INTERNAL.withDescription(e.message).asRuntimeException()
        }

        if (validationResult.isSuccess) {
            super.onMessage(message)
        } else {
            throw Status
                .INVALID_ARGUMENT
                .withDescription(validationResult.toString())
                .asRuntimeException()
        }
    }
}
