// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgIf } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Edit, EditStatus } from '../edit';

@Component({
    selector: 'app-edit-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule, NgIf],
    templateUrl: './edit-card.component.html',
})
export class EditCardComponent {
    @Input({ required: true }) edit!: Edit;
    @Output() submitForReview = new EventEmitter<string>();

    editStatusEnum = EditStatus;

    onSubmitForReview(): void {
        this.submitForReview.emit(this.edit.id);
    }
}
