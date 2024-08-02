// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Draft, DraftStatus } from '../draft';

@Component({
    selector: 'app-draft-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './draft-card.component.html',
})
export class DraftCardComponent {
    @Input({ required: true }) draft!: Draft;
    @Output() delete = new EventEmitter<string>();
    @Output() submitForReview = new EventEmitter<string>();

    draftStatusEnum = DraftStatus;

    canDelete(): boolean {
        return this.draft.status === DraftStatus.Unsubmitted ||
            this.draft.status === DraftStatus.Submitted;
    }

    onDelete(): void {
        this.delete.emit(this.draft.id);
    }

    onSubmitForReview(): void {
        this.submitForReview.emit(this.draft.id);
    }
}
