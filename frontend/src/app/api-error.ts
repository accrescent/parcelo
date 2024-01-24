// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

export interface ApiError {
    error_code: number;
    title: string;
    message: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function isApiError(error: any): error is ApiError {
    return error
        && 'error_code' in error && typeof error.error_code === 'number'
        && 'title' in error && typeof error.title === 'string'
        && 'message' in error && typeof error.message === 'string';
}
