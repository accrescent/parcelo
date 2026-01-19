// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.AppEditService
import app.accrescent.appstore.publish.v1alpha1.CreateAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.GetAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.SubmitAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.SubmitAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.UpdateAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.UpdateAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.appEdit
import app.accrescent.appstore.publish.v1alpha1.appPackage
import app.accrescent.appstore.publish.v1alpha1.createAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.getAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.updateAppEditResponse
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.AppEditListing
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import app.accrescent.server.parcelo.security.ObjectReference
import app.accrescent.server.parcelo.security.ObjectType
import app.accrescent.server.parcelo.security.Permission
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.protobuf.timestamp
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppEditServiceImpl @Inject constructor(
    private val permissionService: PermissionService,
) : AppEditService {
    @Transactional
    override fun createAppEdit(request: CreateAppEditRequest): Uni<CreateAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canCreateAppEdit = permissionService.hasPermission(
            ObjectReference(ObjectType.APP, request.appId),
            Permission.CREATE_APP_EDIT,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canCreateAppEdit) {
            val appExists = App.existsById(request.appId)
            val canViewAppExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP, request.appId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!canViewAppExistence || !appExists) {
                appNotFoundException(request.appId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription(
                        "insufficient permission to create edits for app \"${request.appId}\""
                    )
                    .asRuntimeException()
            }
        }
        val app = App.findById(request.appId) ?: throw appNotFoundException(request.appId)

        val appActiveEditLimit = app.activeEditLimit
        val appActiveEditCount = AppEdit.countActiveForApp(app.id)
        if (appActiveEditCount >= appActiveEditLimit) {
            throw Status
                .RESOURCE_EXHAUSTED
                .withDescription("app limit of $appActiveEditLimit active edits already reached")
                .asRuntimeException()
        }

        val appEdit = AppEdit(
            id = Identifier.generateNew(IdType.APP_EDIT),
            appId = request.appId,
            createdAt = OffsetDateTime.now(),
            defaultListingLanguage = app.defaultListingLanguage,
            appPackageId = app.appPackageId,
            reviewId = null,
            publishedAt = null,
        )
            .also { it.persist() }
        for (listing in app.listings) {
            AppEditListing(
                id = UUID.randomUUID(),
                appEditId = appEdit.id,
                language = listing.language,
                name = listing.name,
                shortDescription = listing.shortDescription,
                iconImageId = listing.iconImageId,
            )
                .persist()
        }

        val response = createAppEditResponse { appEditId = appEdit.id }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppEdit(request: GetAppEditRequest): Uni<GetAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canView = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_EDIT, request.appEditId),
            Permission.VIEW,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canView) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_EDIT, request.appEditId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to view app edit")
                    .asRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        val response = getAppEditResponse {
            this.appEdit = appEdit {
                id = appEdit.id
                createdAt = timestamp {
                    seconds = appEdit.createdAt.toEpochSecond()
                    nanos = appEdit.createdAt.nano
                }
                defaultListingLanguage = appEdit.defaultListingLanguage
                appPackage = appPackage {
                    appId = appEdit.appPackage.appId
                    versionCode = appEdit.appPackage.versionCode.toLong()
                    versionName = appEdit.appPackage.versionName
                    targetSdk = appEdit.appPackage.targetSdk.toLong()
                }
                appEdit.publishedAt?.let { publicationTimestamp ->
                    publishedAt = timestamp {
                        seconds = publicationTimestamp.toEpochSecond()
                        nanos = publicationTimestamp.nano
                    }
                }
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun updateAppEdit(request: UpdateAppEditRequest): Uni<UpdateAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canUpdate = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_EDIT, request.appEditId),
            Permission.UPDATE,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canUpdate) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_EDIT, request.appEditId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to update app edit")
                    .asRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)

        // Update the app edit based on the update mask
        if (request.updateMask.pathsList.contains("default_listing_language")) {
            // Ensure referential integrity by requiring the default listing language to match an
            // existing listing for the app edit
            if (appEdit.hasListingForLanguage(request.defaultListingLanguage)) {
                appEdit.defaultListingLanguage = request.defaultListingLanguage
            } else {
                throw Status
                    .FAILED_PRECONDITION
                    .withDescription(
                        "no listing exists for default listing language " +
                                "\"${request.defaultListingLanguage}\""
                    )
                    .asRuntimeException()
            }
        }

        return Uni.createFrom().item { updateAppEditResponse {} }
    }

    override fun submitAppEdit(request: SubmitAppEditRequest): Uni<SubmitAppEditResponse> {
        throw Status.UNIMPLEMENTED.asRuntimeException()
    }

    private companion object {
        private fun appNotFoundException(appId: String) = Status
            .NOT_FOUND
            .withDescription("app with ID \"$appId\" not found")
            .asRuntimeException()

        private fun appEditNotFoundException(appEditId: String) = Status
            .NOT_FOUND
            .withDescription("app edit with ID \"$appEditId\" not found")
            .asRuntimeException()
    }
}
