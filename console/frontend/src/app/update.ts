// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

export interface Update {
    id: string;
    app_id: string;
    version_code: number;
    version_name: string;
    creation_time: number;
    requires_review: boolean;
    status: UpdateStatus;
}

export enum UpdateStatus {
    Unsubmitted = 'unsubmitted',
    PendingReview = 'pending-review',
    Publishing = 'publishing',
    Published = 'published',
}
