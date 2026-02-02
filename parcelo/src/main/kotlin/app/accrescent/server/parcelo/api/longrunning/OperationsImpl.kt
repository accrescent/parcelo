// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.longrunning

import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.BackgroundJob
import app.accrescent.server.parcelo.data.BackgroundJobType
import app.accrescent.server.parcelo.jobs.JobDataKey
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.ObjectReference
import app.accrescent.server.parcelo.security.ObjectType
import app.accrescent.server.parcelo.security.Permission
import app.accrescent.server.parcelo.security.PermissionService
import com.google.longrunning.GetOperationRequest
import com.google.longrunning.Operation
import com.google.longrunning.OperationsGrpc
import com.google.protobuf.Any
import com.google.rpc.Code
import io.grpc.Status
import io.grpc.stub.StreamObserver
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.context.ManagedExecutor
import org.quartz.JobKey
import org.quartz.Scheduler
import com.google.rpc.Status as GoogleStatus

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class OperationsImpl @Inject constructor(
    private val executor: ManagedExecutor,
    private val permissionService: PermissionService,
    private val scheduler: Scheduler,
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
        val metadata = BackgroundJob.findByJobName(request.name) ?: run {
            responseObserver.onError(operationNotFoundException(request.name))
            return
        }
        val jobDetail = scheduler.getJobDetail(JobKey.jobKey(request.name)) ?: run {
            responseObserver.onError(operationNotFoundException(request.name))
            return
        }

        val resource = when (metadata.type) {
            BackgroundJobType.PUBLISH_APP_DRAFT ->
                ObjectReference(ObjectType.APP_DRAFT, metadata.parentId)

            BackgroundJobType.PUBLISH_APP_EDIT ->
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

        val success = if (jobDetail.jobDataMap.containsKey(JobDataKey.SUCCESS)) {
            jobDetail.jobDataMap.getBoolean(JobDataKey.SUCCESS)
        } else {
            null
        }

        val operation = Operation
            .newBuilder()
            .apply {
                name = request.name
                done = success != null
                if (success != null) {
                    if (success) {
                        response = Any.getDefaultInstance()
                    } else {
                        error = GoogleStatus
                            .newBuilder()
                            .apply { code = Code.INTERNAL_VALUE }
                            .build()
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
