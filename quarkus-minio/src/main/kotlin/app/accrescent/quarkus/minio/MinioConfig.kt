// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.minio

import io.quarkus.runtime.annotations.ConfigPhase
import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import java.util.Optional

@ConfigMapping(prefix = "quarkus.minio")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
interface MinioConfig {
    fun host(): String

    fun accessKey(): String

    fun secretKey(): String
}
