// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes.auth

import kotlinx.serialization.Serializable


@Serializable
data class Permissions(
    val reviewer: Boolean,
    val publisher: Boolean
)