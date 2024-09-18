// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object BaselineListings : IntIdTable("listings") {
    val appId = reference("app_id", BaselineApps, ReferenceOption.CASCADE)
    val locale = text("locale")
    val iconId = reference("icon_id", BaselineIcons, ReferenceOption.NO_ACTION)
    val label = text("label")
    val shortDescription = text("short_description")

    init {
        uniqueIndex(appId, locale)
    }
}
