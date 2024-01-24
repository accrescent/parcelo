// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

import { App } from '../app';

@Component({
    selector: 'app-app-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule, RouterLink],
    templateUrl: './app-card.component.html',
})
export class AppCardComponent {
    @Input({ required: true }) app!: App;
}
