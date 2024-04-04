// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Listings : IntIdTable("listings") {
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)
    val locale = text("locale")
    val iconId = reference("icon_id", Icons, ReferenceOption.NO_ACTION)
    val label = text("label")
    val shortDescription = text("short_description")

    init {
        uniqueIndex(appId, locale)
    }
}

class Listing(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Listing>(Listings)

    var appId by Listings.appId
    var locale by Listings.locale
    var iconId by Listings.iconId
    var label by Listings.label
    var shortDescription by Listings.shortDescription
}
