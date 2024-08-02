// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Edit, EditStatus } from '../edit';

@Component({
    selector: 'app-edit-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './edit-card.component.html',
})
export class EditCardComponent {
    @Input({ required: true }) edit!: Edit;
    @Output() delete = new EventEmitter<string>();
    @Output() submitForReview = new EventEmitter<string>();

    editStatusEnum = EditStatus;

    canDelete(): boolean {
        return this.edit.status === EditStatus.Unsubmitted ||
            this.edit.status === EditStatus.Submitted;
    }

    onDelete(): void {
        this.delete.emit(this.edit.id);
    }

    onSubmitForReview(): void {
        this.submitForReview.emit(this.edit.id);
    }
}
