// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.longrunning

import app.accrescent.appstore.publish.v1alpha1.PublishAppDraftResult
import app.accrescent.appstore.publish.v1alpha1.PublishAppEditResult
import app.accrescent.appstore.publish.v1alpha1.UploadAppDraftListingIconResult
import app.accrescent.appstore.publish.v1alpha1.UploadAppDraftResult
import app.accrescent.appstore.publish.v1alpha1.UploadAppEditResult
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.ObjectReference
import app.accrescent.server.parcelo.security.ObjectType
import app.accrescent.server.parcelo.security.Permission
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
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.context.ManagedExecutor
import org.quartz.Scheduler
import com.google.rpc.Status as GoogleStatus

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class OperationsImpl @Inject constructor(
    private val executor: ManagedExecutor,
    private val permissionService: PermissionService,
    private val scheduler: Instance<Scheduler>,
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

        val resource = when (metadata.type) {
            BackgroundOperationType.PUBLISH_APP_DRAFT,
            BackgroundOperationType.UPLOAD_APP_DRAFT,
            BackgroundOperationType.UPLOAD_APP_DRAFT_LISTING_ICON ->
                ObjectReference(ObjectType.APP_DRAFT, metadata.parentId)

            BackgroundOperationType.PUBLISH_APP_EDIT,
            BackgroundOperationType.UPLOAD_APP_EDIT ->
                ObjectReference(ObjectType.APP_EDIT, metadata.parentId)
        }
        val canView = permissionService.hasPermission(
            resource,
            Permission.VIEW,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canView) {
            val exists = when (resource.type) {
                ObjectType.APP_DRAFT -> AppDraft.existsById(resource.id)
                else -> false
            }
            val canViewExistence = permissionService.hasPermission(
                resource,
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
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
