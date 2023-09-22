// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { Update } from '../update';

@Component({
    selector: 'app-update-card',
    standalone: true,
    imports: [MatCardModule],
    templateUrl: './update-card.component.html',
})
export class UpdateCardComponent {
    @Input({ required: true }) update!: Update;
}
