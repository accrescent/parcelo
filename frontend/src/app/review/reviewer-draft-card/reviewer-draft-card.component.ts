// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Draft } from '../../draft';

@Component({
    selector: 'app-reviewer-draft-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './reviewer-draft-card.component.html',
})
export class ReviewerDraftCardComponent {
    @Input({ required: true }) draft!: Draft;
    @Output() postReview = new EventEmitter<string>();

    onPostReview(): void {
        this.postReview.emit(this.draft.id);
    }
}
