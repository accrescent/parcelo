// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

import { App } from '../app';
import { Update } from '../update';

@Component({
    selector: 'app-update-submission-dialog',
    standalone: true,
    imports: [MatButtonModule, MatDialogModule],
    templateUrl: './update-submission-dialog.component.html',
    styleUrl: './update-submission-dialog.component.scss'
})
export class UpdateSubmissionDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: { app: App, update: Update }) {}
}
