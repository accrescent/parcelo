// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgFor, NgIf } from '@angular/common';
import { Component, EventEmitter, Output } from '@angular/core';
import { FormArray, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';

import { Review, ReviewResult } from '../review';

@Component({
    selector: 'app-review-editor',
    standalone: true,
    imports: [
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatRadioModule,
        NgFor,
        NgIf,
        ReactiveFormsModule,
    ],
    templateUrl: './review-editor.component.html',
    styleUrls: ['./review-editor.component.scss']
})
export class ReviewEditorComponent {
    @Output() formSubmit = new EventEmitter<Review>();

    reviewResultEnum = ReviewResult;

    form = this.fb.group({
        result: [ReviewResult.Rejected, Validators.required],
        reasons: this.fb.array([this.fb.control('', Validators.required)], Validators.required),
        additional_notes: [''],
    });

    constructor(private fb: NonNullableFormBuilder) {}

    get reasons(): FormArray {
        return this.form.controls['reasons'] as FormArray;
    }

    addReason(): void {
        this.reasons.push(this.fb.control('', Validators.required));
    }

    removeReason(index: number): void {
        this.reasons.removeAt(index);
    }

    updateFormFields(result: ReviewResult): void {
        // Enable the 'reasons' field if and only if the review is a rejection
        switch (result) {
        case ReviewResult.Approved:
            this.form.controls['reasons'].disable();
            break;
        case ReviewResult.Rejected:
            this.form.controls['reasons'].enable();
            break;
        }
    }

    emitForm(): void {
        const form = this.form.value;

        // Remove unintentional whitespace
        form.additional_notes = form.additional_notes?.trim();

        // Don't send this field if there aren't any notes to avoid creating unnecessary database
        // entries
        if (form.additional_notes === '') {
            form.additional_notes = undefined;
        }

        const review: Review = {
            result: form.result!,
            reasons: form.reasons,
            additional_notes: form.additional_notes,
        };
        this.formSubmit.emit(review);
    }
}
