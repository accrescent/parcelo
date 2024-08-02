// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { NewUpdateForm } from '../new-update-form';

@Component({
    selector: 'app-new-update-editor',
    standalone: true,
    imports: [MatButtonModule, MatCardModule, ReactiveFormsModule],
    templateUrl: './new-update-editor.component.html',
    styleUrl: './new-update-editor.component.scss'
})
export class NewUpdateEditorComponent {
    @Input() submitDisabled = false;
    @Output() formSubmit = new EventEmitter<NewUpdateForm>();

    form = this.fb.group({
        apkSet: ['', Validators.required],
    });

    constructor(private fb: NonNullableFormBuilder) {}

    emitForm(): void {
        const apkSet = (<HTMLInputElement>document.getElementById('apkset')).files?.[0];

        if (apkSet !== undefined) {
            const form: NewUpdateForm = { apkSet: apkSet };

            this.formSubmit.emit(form);
        }
    }
}
