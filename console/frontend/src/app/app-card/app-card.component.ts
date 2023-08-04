// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

import { App } from '../app';

@Component({
    selector: 'app-app-card',
    standalone: true,
    imports: [CommonModule, MatCardModule],
    templateUrl: './app-card.component.html',
})
export class AppCardComponent {
    @Input({ required: true }) app!: App;
}
