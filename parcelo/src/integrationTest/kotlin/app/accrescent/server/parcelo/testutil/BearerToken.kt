// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.testutil

import io.grpc.CallCredentials
import io.grpc.Metadata
import java.util.concurrent.Executor

data class BearerToken(val token: String) : CallCredentials() {
    companion object {
        private val AUTHORIZATION_HEADER_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun applyRequestMetadata(
        requestInfo: RequestInfo,
        appExecutor: Executor,
        applier: MetadataApplier,
    ) {
        val metadata = Metadata().apply { put(AUTHORIZATION_HEADER_KEY, "Bearer $token") }
        applier.apply(metadata)
    }
}
