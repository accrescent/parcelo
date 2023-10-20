// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Update, UpdateStatus } from '../update';

@Component({
    selector: 'app-update-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './update-card.component.html',
})
export class UpdateCardComponent {
    @Input({ required: true }) update!: Update;
    @Output() delete = new EventEmitter<string>();
    @Output() submitForReview = new EventEmitter<string>();

    updateStatusEnum = UpdateStatus;

    onDelete(): void {
        this.delete.emit(this.update.id);
    }

    onSubmitForReview(): void {
        this.submitForReview.emit(this.update.id);
    }
}
