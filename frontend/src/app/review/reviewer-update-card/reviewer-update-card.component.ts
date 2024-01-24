// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Update } from '../../update';

@Component({
    selector: 'app-reviewer-update-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './reviewer-update-card.component.html',
})
export class ReviewerUpdateCardComponent {
    @Input({ required: true }) update!: Update;
    @Output() postReview = new EventEmitter<string>();

    onPostReview(): void {
        this.postReview.emit(this.update.id);
    }
}
