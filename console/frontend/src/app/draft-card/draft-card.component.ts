// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Draft, DraftStatus } from '../app/draft';

@Component({
    selector: 'app-draft-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './draft-card.component.html',
    styleUrls: ['./draft-card.component.scss']
})
export class DraftCardComponent {
    @Input({ required: true }) draft!: Draft;
    @Output() submitForReview = new EventEmitter<string>();

    draftStatusEnum = DraftStatus;

    onSubmitForReview(): void {
        this.submitForReview.emit(this.draft.id);
    }
}
