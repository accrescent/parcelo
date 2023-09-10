// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

export interface Permissions {
	reviewer: boolean,
	publisher: boolean
}

export enum AuthError {
	BAD_REQUEST,
	NOT_WHITELISTED,
	UNKNOWN_ERROR
}
