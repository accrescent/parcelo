// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.validation

import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftListingRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftReviewRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppEditListingRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.CreatePublisherRequest
import app.accrescent.appstore.publish.v1alpha1.CreateReviewerRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftListingRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppEditListingRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftDownloadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftListingIconDownloadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftListingIconUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppEditDownloadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppEditUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppRequest
import app.accrescent.appstore.publish.v1alpha1.GetSelfRequest
import app.accrescent.appstore.publish.v1alpha1.ListAppDraftsRequest
import app.accrescent.appstore.publish.v1alpha1.ListAppEditsRequest
import app.accrescent.appstore.publish.v1alpha1.ListAppsRequest
import app.accrescent.appstore.publish.v1alpha1.ListOrganizationsRequest
import app.accrescent.appstore.publish.v1alpha1.PublishAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.SubmitAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.SubmitAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.UpdateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.UpdateAppEditRequest
import app.accrescent.appstore.v1.GetAppDownloadInfoRequest
import app.accrescent.appstore.v1.GetAppListingRequest
import app.accrescent.appstore.v1.GetAppPackageInfoRequest
import app.accrescent.appstore.v1.GetAppUpdateInfoRequest
import app.accrescent.appstore.v1.ListAppListingsRequest
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
                CreateAppDraftListingRequest.getDescriptor(),
                CreateAppDraftRequest.getDescriptor(),
                CreateAppDraftReviewRequest.getDescriptor(),
                CreateAppEditListingRequest.getDescriptor(),
                CreateAppEditRequest.getDescriptor(),
                CreatePublisherRequest.getDescriptor(),
                CreateReviewerRequest.getDescriptor(),
                DeleteAppDraftListingRequest.getDescriptor(),
                DeleteAppDraftRequest.getDescriptor(),
                DeleteAppEditListingRequest.getDescriptor(),
                DeleteAppEditRequest.getDescriptor(),
                GetAppDownloadInfoRequest.getDescriptor(),
                GetAppDraftDownloadInfoRequest.getDescriptor(),
                GetAppDraftListingIconDownloadInfoRequest.getDescriptor(),
                GetAppDraftListingIconUploadInfoRequest.getDescriptor(),
                GetAppDraftRequest.getDescriptor(),
                GetAppDraftUploadInfoRequest.getDescriptor(),
                GetAppEditDownloadInfoRequest.getDescriptor(),
                GetAppEditUploadInfoRequest.getDescriptor(),
                GetAppEditRequest.getDescriptor(),
                GetAppListingRequest.getDescriptor(),
                GetAppPackageInfoRequest.getDescriptor(),
                GetAppRequest.getDescriptor(),
                GetAppUpdateInfoRequest.getDescriptor(),
                GetSelfRequest.getDescriptor(),
                ListAppDraftsRequest.getDescriptor(),
                ListAppEditsRequest.getDescriptor(),
                ListAppListingsRequest.getDescriptor(),
                ListAppsRequest.getDescriptor(),
                ListOrganizationsRequest.getDescriptor(),
                PublishAppDraftRequest.getDescriptor(),
                SubmitAppDraftRequest.getDescriptor(),
                SubmitAppEditRequest.getDescriptor(),
                UpdateAppDraftRequest.getDescriptor(),
                UpdateAppEditRequest.getDescriptor(),
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
