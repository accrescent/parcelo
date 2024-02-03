// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import org.jobrunr.configuration.JobRunr
import org.jobrunr.scheduling.BackgroundJob
import org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider
import java.time.Duration
import javax.sql.DataSource

private const val FILE_CLEANING_LABEL = "CLEAN_FILES"
private val FILE_CLEANING_PERIOD = Duration.ofHours(6)

/**
 * Configures JobRunr with the given [DataSource]
 */
fun configureJobRunr(dataSource: DataSource) {
    JobRunr
        .configure()
        .useStorageProvider(SqLiteStorageProvider(dataSource))
        .useBackgroundJobServer()
        .initialize()

    BackgroundJob.scheduleRecurrently(FILE_CLEANING_LABEL, FILE_CLEANING_PERIOD) {
        cleanDeletedFiles()
    }
}
