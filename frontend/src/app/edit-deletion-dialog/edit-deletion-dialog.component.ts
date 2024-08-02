// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

import { Edit } from '../edit';

@Component({
    selector: 'app-edit-deletion-dialog',
    standalone: true,
    imports: [MatButtonModule, MatDialogModule],
    templateUrl: './edit-deletion-dialog.component.html',
    styleUrl: './edit-deletion-dialog.component.scss'
})
export class EditDeletionDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: Edit) {}
}
