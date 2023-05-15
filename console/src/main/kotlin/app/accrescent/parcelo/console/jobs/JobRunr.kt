package app.accrescent.parcelo.console.jobs

import org.jobrunr.configuration.JobRunr
import org.jobrunr.storage.InMemoryStorageProvider

fun configureJobRunr() {
    JobRunr
        .configure()
        .useStorageProvider(InMemoryStorageProvider())
        .useBackgroundJobServer()
        .initialize()
}
