// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { HttpErrorResponse, HttpInterceptorFn, HttpStatusCode } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

import { AuthService } from './auth.service';

export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    return next(req).pipe(tap(
        undefined,
        (e) => {
            if (e instanceof HttpErrorResponse && e.status === HttpStatusCode.Unauthorized) {
                authService.logOut().subscribe(() => {
                    // This reloads the page (ensuring the login screen is shown instead of a stale
                    // view of the current page) without fetching data from the server.
                    //
                    // eslint-disable-next-line no-self-assign
                    router.navigate(['/']).then(() => window.location.href = window.location.href);
                });
            }
        }
    ));
};
