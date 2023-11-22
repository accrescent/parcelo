// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

export interface Edit {
    id: string;
    app_id: string;
    short_description?: string;
    creation_time: number;
    status: EditStatus;
}

export enum EditStatus {
    Unsubmitted = 'unsubmitted',
    Submitted = 'submitted',
    Rejected = 'rejected',
    Publishing = 'publishing',
    Published = 'published',
}
