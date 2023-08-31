// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly sessionUrl = 'api/v1/session';

    constructor(private http: HttpClient) {}

    logOut(): Observable<void> {
        return this.http.delete<void>(this.sessionUrl);
    }
}
