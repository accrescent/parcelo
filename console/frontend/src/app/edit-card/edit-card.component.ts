// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgIf } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { Edit } from '../edit';

@Component({
    selector: 'app-edit-card',
    standalone: true,
    imports: [MatCardModule, NgIf],
    templateUrl: './edit-card.component.html',
})
export class EditCardComponent {
    @Input({ required: true }) edit!: Edit;
}
