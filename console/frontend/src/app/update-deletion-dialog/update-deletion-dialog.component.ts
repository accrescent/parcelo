// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

import { Update } from '../update';

@Component({
    selector: 'app-update-deletion-dialog',
    standalone: true,
    imports: [MatButtonModule, MatDialogModule],
    templateUrl: './update-deletion-dialog.component.html',
    styleUrls: ['./update-deletion-dialog.component.scss']
})
export class UpdateDeletionDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: Update) {}
}
