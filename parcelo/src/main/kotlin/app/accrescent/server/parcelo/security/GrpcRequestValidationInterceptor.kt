// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.appstore.v1.GetAppDownloadInfoRequest
import app.accrescent.appstore.v1.GetAppListingRequest
import app.accrescent.appstore.v1.GetAppPackageInfoRequest
import app.accrescent.appstore.v1.GetAppUpdateInfoRequest
import app.accrescent.console.v1.CreateAppDraftListingRequest
import app.accrescent.console.v1.CreateAppDraftRequest
import app.accrescent.console.v1.CreateAppDraftReviewRequest
import app.accrescent.console.v1.CreateAppEditListingRequest
import app.accrescent.console.v1.CreateAppEditRequest
import app.accrescent.console.v1.CreateAppEditReviewRequest
import app.accrescent.console.v1.DeleteAppDraftListingRequest
import app.accrescent.console.v1.DeleteAppDraftRequest
import app.accrescent.console.v1.DeleteAppEditListingRequest
import app.accrescent.console.v1.DeleteAppEditRequest
import app.accrescent.console.v1.DownloadAppDraftListingIconRequest
import app.accrescent.console.v1.DownloadAppDraftRequest
import app.accrescent.console.v1.DownloadAppEditRequest
import app.accrescent.console.v1.GetAppDraftRequest
import app.accrescent.console.v1.GetAppEditRequest
import app.accrescent.console.v1.GetAppRequest
import app.accrescent.console.v1.GetOrganizationRequest
import app.accrescent.console.v1.GetSelfRequest
import app.accrescent.console.v1.ListAppDraftsRequest
import app.accrescent.console.v1.ListAppEditsRequest
import app.accrescent.console.v1.ListAppsRequest
import app.accrescent.console.v1.ListOrganizationsRequest
import app.accrescent.console.v1.PublishAppDraftRequest
import app.accrescent.console.v1.SubmitAppDraftRequest
import app.accrescent.console.v1.SubmitAppEditRequest
import app.accrescent.console.v1.UpdateAppDraftRequest
import app.accrescent.console.v1.UpdateAppEditRequest
import app.accrescent.console.v1.UpdateAppRequest
import app.accrescent.console.v1.UpdateUserRequest
import app.accrescent.console.v1.UploadAppDraftListingIconRequest
import app.accrescent.console.v1.UploadAppDraftRequest
import app.accrescent.console.v1.UploadAppEditListingIconRequest
import app.accrescent.console.v1.UploadAppEditRequest
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
import app.accrescent.appstore.v1.ListAppListingsRequest as AppStoreListAppListingsRequest
import app.accrescent.console.v1.ListAppListingsRequest as ConsoleListAppListingsRequest

@ApplicationScoped
class GrpcRequestValidationInterceptor : ServerInterceptor {
    private val validator = ValidatorFactory
        .newBuilder()
        .buildWithDescriptors(
            listOf(
                AppStoreListAppListingsRequest.getDescriptor(),
                ConsoleListAppListingsRequest.getDescriptor(),
                CreateAppDraftListingRequest.getDescriptor(),
                CreateAppDraftRequest.getDescriptor(),
                CreateAppDraftReviewRequest.getDescriptor(),
                CreateAppEditListingRequest.getDescriptor(),
                CreateAppEditRequest.getDescriptor(),
                CreateAppEditReviewRequest.getDescriptor(),
                DeleteAppDraftListingRequest.getDescriptor(),
                DeleteAppDraftRequest.getDescriptor(),
                DeleteAppEditListingRequest.getDescriptor(),
                DeleteAppEditRequest.getDescriptor(),
                DownloadAppDraftRequest.getDescriptor(),
                DownloadAppDraftListingIconRequest.getDescriptor(),
                DownloadAppEditRequest.getDescriptor(),
                GetAppDownloadInfoRequest.getDescriptor(),
                GetAppDraftRequest.getDescriptor(),
                GetAppEditRequest.getDescriptor(),
                GetAppListingRequest.getDescriptor(),
                GetAppPackageInfoRequest.getDescriptor(),
                GetAppRequest.getDescriptor(),
                GetAppUpdateInfoRequest.getDescriptor(),
                GetOrganizationRequest.getDescriptor(),
                GetSelfRequest.getDescriptor(),
                ListAppDraftsRequest.getDescriptor(),
                ListAppEditsRequest.getDescriptor(),
                ListAppsRequest.getDescriptor(),
                ListOrganizationsRequest.getDescriptor(),
                PublishAppDraftRequest.getDescriptor(),
                SubmitAppDraftRequest.getDescriptor(),
                SubmitAppEditRequest.getDescriptor(),
                UpdateAppDraftRequest.getDescriptor(),
                UpdateAppEditRequest.getDescriptor(),
                UpdateAppRequest.getDescriptor(),
                UpdateUserRequest.getDescriptor(),
                UploadAppDraftListingIconRequest.getDescriptor(),
                UploadAppDraftRequest.getDescriptor(),
                UploadAppEditListingIconRequest.getDescriptor(),
                UploadAppEditRequest.getDescriptor(),
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
