// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.testutil

import io.grpc.CallCredentials
import io.grpc.Metadata
import io.quarkus.oidc.runtime.OidcUtils
import java.util.concurrent.Executor

data class OidcCookieCallCredentials(val cookieValue: String) : CallCredentials() {
    private companion object {
        private val COOKIE_HEADER_KEY = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun applyRequestMetadata(
        requestInfo: RequestInfo,
        appExecutor: Executor,
        applier: MetadataApplier,
    ) {
        val metadata = Metadata()
            .apply { put(COOKIE_HEADER_KEY, "${OidcUtils.SESSION_COOKIE_NAME}=$cookieValue") }
        applier.apply(metadata)
    }
}
