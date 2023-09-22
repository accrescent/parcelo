// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.File as FileDao
import app.accrescent.parcelo.console.data.Files
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.io.IOException

fun cleanFile(fileId: Int) {
    val file = transaction {
        FileDao.find { Files.id eq fileId and Files.deleted }.singleOrNull()
    } ?: return
    if (File(file.localPath).delete()) {
        transaction { file.delete() }
    } else {
        throw IOException("file cleaning failed")
    }
}
