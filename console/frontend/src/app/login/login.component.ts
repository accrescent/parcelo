// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { FormContainerComponent } from '../form-container/form-container.component';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    standalone: true,
    imports: [MatButtonModule, FormContainerComponent],
})
export class LoginComponent { }
