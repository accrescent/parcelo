// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.appstore

import app.accrescent.appstore.v1.AppService
import app.accrescent.appstore.v1.DeviceAttributes
import app.accrescent.appstore.v1.GetAppDownloadInfoRequest
import app.accrescent.appstore.v1.GetAppDownloadInfoResponse
import app.accrescent.appstore.v1.GetAppListingRequest
import app.accrescent.appstore.v1.GetAppListingResponse
import app.accrescent.appstore.v1.GetAppPackageInfoRequest
import app.accrescent.appstore.v1.GetAppPackageInfoResponse
import app.accrescent.appstore.v1.GetAppUpdateInfoRequest
import app.accrescent.appstore.v1.GetAppUpdateInfoResponse
import app.accrescent.appstore.v1.ListAppListingsRequest
import app.accrescent.appstore.v1.ListAppListingsResponse
import app.accrescent.appstore.v1.appDownloadInfo
import app.accrescent.appstore.v1.appListing
import app.accrescent.appstore.v1.appUpdateInfo
import app.accrescent.appstore.v1.getAppDownloadInfoResponse
import app.accrescent.appstore.v1.getAppListingResponse
import app.accrescent.appstore.v1.getAppPackageInfoResponse
import app.accrescent.appstore.v1.getAppUpdateInfoResponse
import app.accrescent.appstore.v1.image
import app.accrescent.appstore.v1.listAppListingsResponse
import app.accrescent.appstore.v1.packageInfo
import app.accrescent.appstore.v1.splitDownloadInfo
import app.accrescent.appstore.v1.splitUpdateInfo
import app.accrescent.parcelo.impl.v1.ListAppListingsPageToken
import app.accrescent.parcelo.impl.v1.listAppListingsPageToken
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDefaultListingLanguage
import app.accrescent.server.parcelo.data.AppListing
import app.accrescent.server.parcelo.data.AppPackage
import app.accrescent.server.parcelo.data.ListingId
import app.accrescent.server.parcelo.data.PublishedApk
import app.accrescent.server.parcelo.data.PublishedImage
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.android.bundle.Commands
import com.android.tools.build.bundletool.device.ApkMatcher
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional
import java.util.Locale
import java.util.Optional
import kotlin.io.encoding.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 200u

@GrpcService
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppServiceImpl(private val config: ParceloConfig) : AppService {
    @Transactional
    override fun getAppListing(request: GetAppListingRequest): Uni<GetAppListingResponse> {
        val app = App.findById(request.appId) ?: throw Status
            .NOT_FOUND
            .withDescription("app with ID \"${request.appId}\" not found")
            .asRuntimeException()
        val availableListingLanguages = AppListing
            .getListingLanguagesForApp(app.id)
            .map { it.language }

        val bestMatchingLanguage = Locale
            .lookupTag(
                request.preferredLanguagesList.map { Locale.LanguageRange(it) },
                availableListingLanguages,
            )
            ?: app.defaultListingLanguage

        val appListing = AppListing
            .findByAppIdAndLanguage(app.id, bestMatchingLanguage)
            ?: throw Status
                .DATA_LOSS
                .withDescription("listing for best matching language not found")
                .asRuntimeException()
        val response = getAppListingResponse {
            listing = appListing {
                appId = app.id
                language = appListing.language
                name = appListing.name
                shortDescription = appListing.shortDescription
                icon = image {
                    url = getPublishedIconUrl(appListing.icon)
                }
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun listAppListings(request: ListAppListingsRequest): Uni<ListAppListingsResponse> {
        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val skip = if (request.hasSkip()) request.skip.toUInt() else 0u
        val pageToken = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.UrlSafe.decode(request.pageToken)
                val token = ListAppListingsPageToken.parseFrom(tokenBytes)
                if (!token.hasLastAppId()) {
                    throw invalidPageTokenError.asRuntimeException()
                }

                token
            } catch (_: IllegalArgumentException) {
                throw invalidPageTokenError.asRuntimeException()
            } catch (_: InvalidProtocolBufferException) {
                throw invalidPageTokenError.asRuntimeException()
            }
        } else {
            null
        }

        val defaultListingLanguages = App
            .findDefaultListingLanguagesByQuery(pageSize, skip, pageToken?.lastAppId)
            .associateBy(
                AppDefaultListingLanguage::id,
                AppDefaultListingLanguage::defaultListingLanguage,
            )
        val listingIds = AppListing.findIdsForApps(defaultListingLanguages.keys)
        val bestMatchingLanguages = listingIds
            .groupBy(ListingId::appId, ListingId::language)
            .map { (appId, availableLanguages) ->
                val bestMatchingLanguage = Locale
                    .lookupTag(
                        request.preferredLanguagesList.map { Locale.LanguageRange(it) },
                        availableLanguages,
                    )
                    ?: defaultListingLanguages[appId]
                    ?: throw Status
                        .DATA_LOSS
                        .withDescription("default listing language expected for app ID")
                        .asRuntimeException()

                appId to bestMatchingLanguage
            }
        val listings = AppListing
            .findByIdsOrdered(bestMatchingLanguages)
            .map {
                appListing {
                    appId = it.appId
                    language = it.language
                    name = it.name
                    shortDescription = it.shortDescription
                    icon = image {
                        url = getPublishedIconUrl(it.icon)
                    }
                }
            }

        val response = if (listings.isNotEmpty()) {
            // Set a page token indicating there may be more results
            val pageToken = listAppListingsPageToken { lastAppId = listings.last().appId }
            val encodedPageToken = Base64.UrlSafe.encode(pageToken.toByteArray())

            listAppListingsResponse {
                this.listings.addAll(listings)
                nextPageToken = encodedPageToken
            }
        } else {
            listAppListingsResponse {}
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppPackageInfo(
        request: GetAppPackageInfoRequest,
    ): Uni<GetAppPackageInfoResponse> {
        val packageInfo = AppPackage.findPackageInfoByPublishedAppId(request.appId) ?: throw Status
            .NOT_FOUND
            .withDescription("app \"${request.appId}\" not found")
            .asRuntimeException()

        val response = getAppPackageInfoResponse {
            this.packageInfo = packageInfo {
                versionCode = packageInfo.versionCode.toLong()
                versionName = packageInfo.versionName
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDownloadInfo(
        request: GetAppDownloadInfoRequest,
    ): Uni<GetAppDownloadInfoResponse> {
        val pkg = AppPackage.findByPublishedAppId(request.appId) ?: throw Status
            .NOT_FOUND
            .withDescription("app \"${request.appId}\" not found")
            .asRuntimeException()
        val buildApksResult = try {
            Commands.BuildApksResult.parseFrom(pkg.buildApksResult)
        } catch (_: InvalidProtocolBufferException) {
            throw Status
                .DATA_LOSS
                .withDescription("app package metadata is invalid")
                .asRuntimeException()
        }

        val matchingApkPaths = getMatchingApkPaths(buildApksResult, request.deviceAttributes)
        if (matchingApkPaths.isEmpty()) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("app is incompatible with the specified device")
                .asRuntimeException()
        }

        val publishedApks = PublishedApk.findByQualifiedPaths(request.appId, matchingApkPaths)
        val response = getAppDownloadInfoResponse {
            appDownloadInfo = appDownloadInfo {
                splitDownloadInfo.addAll(publishedApks.map {
                    splitDownloadInfo {
                        downloadSize = it.size.toInt()
                        url = "${config.artifactsBaseUrl()}/${it.objectId}"
                    }
                })
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppUpdateInfo(request: GetAppUpdateInfoRequest): Uni<GetAppUpdateInfoResponse> {
        val pkg = AppPackage.findByPublishedAppId(request.appId) ?: throw Status
            .NOT_FOUND
            .withDescription("app \"${request.appId}\" not found")
            .asRuntimeException()

        if (request.baseVersionCode >= pkg.versionCode) {
            return Uni.createFrom().item { getAppUpdateInfoResponse {} }
        }

        val buildApksResult = try {
            Commands.BuildApksResult.parseFrom(pkg.buildApksResult)
        } catch (_: InvalidProtocolBufferException) {
            throw Status
                .DATA_LOSS
                .withDescription("app package metadata is invalid")
                .asRuntimeException()
        }

        val matchingApkPaths = getMatchingApkPaths(buildApksResult, request.deviceAttributes)
        if (matchingApkPaths.isEmpty()) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("app is incompatible with the specified device")
                .asRuntimeException()
        }

        val publishedApks = PublishedApk.findByQualifiedPaths(request.appId, matchingApkPaths)
        val response = getAppUpdateInfoResponse {
            appUpdateInfo = appUpdateInfo {
                splitUpdateInfo.addAll(publishedApks.map {
                    splitUpdateInfo {
                        apkDownloadSize = it.size.toInt()
                        apkUrl = "${config.artifactsBaseUrl()}/${it.objectId}"
                    }
                })
            }
        }

        return Uni.createFrom().item { response }
    }

    private companion object {
        private val invalidPageTokenError = Status
            .INVALID_ARGUMENT
            .withDescription("provided page token is invalid")

        private fun getMatchingApkPaths(
            buildApksResult: Commands.BuildApksResult,
            deviceAttributes: DeviceAttributes,
        ): List<String> {
            val paths = try {
                ApkMatcher(
                    deviceAttributes.spec,
                    Optional.empty(),
                    true,
                    false,
                    true,
                )
                    .getMatchingApks(buildApksResult)
            } catch (_: IncompatibleDeviceException) {
                emptyList()
            }
                .map { it.path.toString() }

            return paths
        }
    }

    private fun getPublishedIconUrl(icon: PublishedImage): String {
        return "${config.artifactsBaseUrl()}/${icon.objectId}"
    }
}
