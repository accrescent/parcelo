// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { App } from '../app/app';

@Component({
    selector: 'app-app-card',
    standalone: true,
    imports: [MatCardModule],
    templateUrl: './app-card.component.html',
})
export class AppCardComponent {
    @Input({ required: true }) app!: App;
}