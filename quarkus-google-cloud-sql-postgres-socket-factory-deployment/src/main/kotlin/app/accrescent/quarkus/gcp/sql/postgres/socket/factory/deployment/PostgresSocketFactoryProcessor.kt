// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.sql.postgres.socket.factory.deployment

import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem

private const val FEATURE_NAME = "google-cloud-sql-postgres-socket-factory"

class PostgresSocketFactoryProcessor {
    @BuildStep
    fun feature(): FeatureBuildItem {
        return FeatureBuildItem(FEATURE_NAME)
    }

    @BuildStep
    fun addRuntimeInitializedPackage(): RuntimeInitializedPackageBuildItem {
        return RuntimeInitializedPackageBuildItem("org.xbill.DNS")
    }

    @BuildStep
    fun addRuntimeInitializedClass(): RuntimeInitializedClassBuildItem {
        return RuntimeInitializedClassBuildItem("com.kenai.jffi.internal.Cleaner")
    }
}
