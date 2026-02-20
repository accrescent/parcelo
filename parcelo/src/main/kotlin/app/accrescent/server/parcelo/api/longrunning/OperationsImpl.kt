// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.longrunning

import app.accrescent.console.v1alpha1.PublishAppDraftResult
import app.accrescent.console.v1alpha1.PublishAppEditResult
import app.accrescent.console.v1alpha1.UploadAppDraftListingIconResult
import app.accrescent.console.v1alpha1.UploadAppDraftResult
import app.accrescent.console.v1alpha1.UploadAppEditListingIconResult
import app.accrescent.console.v1alpha1.UploadAppEditResult
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.HasPermissionRequest
import app.accrescent.server.parcelo.security.PermissionService
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.longrunning.GetOperationRequest
import com.google.longrunning.Operation
import com.google.longrunning.OperationsGrpc
import com.google.protobuf.Any
import io.grpc.Status
import io.grpc.stub.StreamObserver
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.context.ManagedExecutor
import com.google.rpc.Status as GoogleStatus

private enum class ParentType { APP_DRAFT, APP_EDIT }

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class OperationsImpl @Inject constructor(
    private val executor: ManagedExecutor,
    private val permissionService: PermissionService,
) : OperationsGrpc.OperationsImplBase() {
    override fun getOperation(
        request: GetOperationRequest,
        responseObserver: StreamObserver<Operation>,
    ) {
        val userId = AuthnContextKey.USER_ID.get()

        executor.submit { getOperationImpl(userId, request, responseObserver) }
    }

    @Transactional
    fun getOperationImpl(
        userId: String,
        request: GetOperationRequest,
        responseObserver: StreamObserver<Operation>,
    ) {
        val metadata = BackgroundOperation.findById(request.name) ?: run {
            responseObserver.onError(operationNotFoundException(request.name))
            return
        }

        val parentType = when (metadata.type) {
            BackgroundOperationType.PUBLISH_APP_DRAFT,
            BackgroundOperationType.UPLOAD_APP_DRAFT,
            BackgroundOperationType.UPLOAD_APP_DRAFT_LISTING_ICON -> ParentType.APP_DRAFT

            BackgroundOperationType.PUBLISH_APP_EDIT,
            BackgroundOperationType.UPLOAD_APP_EDIT,
            BackgroundOperationType.UPLOAD_APP_EDIT_LISTING_ICON -> ParentType.APP_EDIT
        }
        val canView = permissionService.hasPermission(
            when (parentType) {
                ParentType.APP_DRAFT -> HasPermissionRequest.ViewAppDraft(metadata.parentId, userId)
                ParentType.APP_EDIT -> HasPermissionRequest.ViewAppEdit(metadata.parentId, userId)
            },
        )
        if (!canView) {
            val exists = when (parentType) {
                ParentType.APP_DRAFT -> AppDraft.existsById(metadata.parentId)
                ParentType.APP_EDIT -> AppEdit.existsById(metadata.parentId)
            }
            val canViewExistence = permissionService.hasPermission(
                when (parentType) {
                    ParentType.APP_DRAFT ->
                        HasPermissionRequest.ViewAppDraftExistence(metadata.parentId, userId)

                    ParentType.APP_EDIT ->
                        HasPermissionRequest.ViewAppEditExistence(metadata.parentId, userId)
                },
            )

            val error = if (!exists || !canViewExistence) {
                operationNotFoundException(request.name)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to view operation")
                    .asRuntimeException()
            }
            responseObserver.onError(error)
            return
        }

        val result = metadata.result?.let {
            if (metadata.succeeded) {
                when (metadata.type) {
                    BackgroundOperationType.PUBLISH_APP_DRAFT -> PublishAppDraftResult.parseFrom(it)
                    BackgroundOperationType.PUBLISH_APP_EDIT -> PublishAppEditResult.parseFrom(it)
                    BackgroundOperationType.UPLOAD_APP_DRAFT -> UploadAppDraftResult.parseFrom(it)
                    BackgroundOperationType.UPLOAD_APP_DRAFT_LISTING_ICON ->
                        UploadAppDraftListingIconResult.parseFrom(it)

                    BackgroundOperationType.UPLOAD_APP_EDIT -> UploadAppEditResult.parseFrom(it)
                    BackgroundOperationType.UPLOAD_APP_EDIT_LISTING_ICON ->
                        UploadAppEditListingIconResult.parseFrom(it)
                }.right()
            } else {
                GoogleStatus.parseFrom(it).left()
            }
        }

        val operation = Operation
            .newBuilder()
            .apply {
                name = request.name
                done = result != null
                if (result != null) {
                    when (result) {
                        is Either.Left -> error = result.value
                        is Either.Right -> response = Any.pack(result.value)
                    }
                }
            }
            .build()

        responseObserver.onNext(operation)
        responseObserver.onCompleted()
    }

    private companion object {
        private fun operationNotFoundException(name: String) = Status
            .NOT_FOUND
            .withDescription("operation \"$name\" not found")
            .asRuntimeException()
    }
}
