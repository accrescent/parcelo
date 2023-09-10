// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ApiError } from "../net/api";

export type ProgressState = NotLoading | Loading | LoadingFailed;

export interface NotLoading {
    kind: "NotLoading";
}

export interface Loading {
    kind: "Loading";
    progress: number;
}

export interface LoadingFailed {
    kind: "LoadingFailed";
    error: ApiError;
}