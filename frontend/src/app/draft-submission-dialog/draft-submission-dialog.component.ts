// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

import { Draft } from '../draft';

@Component({
    selector: 'app-draft-submission-dialog',
    standalone: true,
    imports: [MatButtonModule, MatDialogModule],
    templateUrl: './draft-submission-dialog.component.html',
    styleUrl: './draft-submission-dialog.component.scss'
})
export class DraftSubmissionDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: Draft) {}
}
