// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgIf } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

import { App } from '../app';
import { Edit } from '../edit';

@Component({
    selector: 'app-edit-submission-dialog',
    standalone: true,
    imports: [MatButtonModule, MatDialogModule, NgIf],
    templateUrl: './edit-submission-dialog.component.html',
    styleUrl: './edit-submission-dialog.component.scss'
})
export class EditSubmissionDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: { app: App, edit: Edit }) {}
}
