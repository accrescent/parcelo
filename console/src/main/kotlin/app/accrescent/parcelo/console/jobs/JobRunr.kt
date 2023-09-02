// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import org.jobrunr.configuration.JobRunr
import org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider
import javax.sql.DataSource

fun configureJobRunr(dataSource: DataSource) {
    JobRunr
        .configure()
        .useStorageProvider(SqLiteStorageProvider(dataSource))
        .useBackgroundJobServer()
        .initialize()
}
