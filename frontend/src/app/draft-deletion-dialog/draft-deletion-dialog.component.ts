// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

import { Draft } from '../draft';

@Component({
    selector: 'app-draft-deletion-dialog',
    standalone: true,
    imports: [MatButtonModule, MatDialogModule],
    templateUrl: './draft-deletion-dialog.component.html',
    styleUrl: './draft-deletion-dialog.component.scss'
})
export class DraftDeletionDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: Draft) {}
}
