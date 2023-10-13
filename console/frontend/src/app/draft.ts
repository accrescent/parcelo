// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

export interface Draft {
    id: string;
    app_id: string;
    label: string;
    version_code: number;
    version_name: string;
    creation_time: number;
    status: DraftStatus;
}

export enum DraftStatus {
    Unsubmitted = 'unsubmitted',
    Submitted = 'submitted',
    Approved = 'approved',
    Publishing = 'publishing',
}
