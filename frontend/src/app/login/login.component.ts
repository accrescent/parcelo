// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrl: './login.component.scss',
    imports: [MatProgressSpinnerModule, NgIf],
    standalone: true,
})
export class LoginComponent implements OnInit {
    loading = true;

    constructor(
        private authService: AuthService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.queryParams.subscribe(params => {
            this.authService.logIn(params['code'], params['state']).subscribe(success => {
                if (success) {
                    this.router.navigate(['apps']);
                } else {
                    this.loading = false;
                }
            });
        });
    }
}
