// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.minio.devservices

import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import java.util.Optional

@ConfigMapping(prefix = "quarkus.minio.devservices")
@ConfigRoot
interface MinioDevServicesConfig {
    @WithDefault("true")
    fun enabled(): Boolean

    fun imageName(): Optional<String>

    fun buckets(): Optional<List<String>>
}
