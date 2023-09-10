// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

export interface ApiError {
    error_code: number;
    title: string;
    message: string;
}