// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpStatusCode } from '@angular/common/http';

import { Observable, catchError, map, of, tap, throwError } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly callbackUrl = 'auth/github/callback2';
    private readonly sessionUrl = 'api/v1/session';
    private readonly loggedInStorageKey = 'loggedIn';

    constructor(private http: HttpClient) {}

    get loggedIn(): boolean {
        return localStorage.getItem(this.loggedInStorageKey) === 'true';
    }

    logIn(code: string, state: string): Observable<boolean> {
        const params = new HttpParams().append('code', code).append('state', state);
        return this.http.get<void>(this.callbackUrl, { observe: 'response', params })
            .pipe(
                map(res => res.status === HttpStatusCode.Ok),
                tap(res => localStorage.setItem(this.loggedInStorageKey, res.toString())),
                catchError(err => {
                    if (err.status === HttpStatusCode.Forbidden) {
                        return of(false);
                    } else {
                        return throwError(err);
                    }
                }),
            );
    }

    logOut(): Observable<void> {
        localStorage.setItem(this.loggedInStorageKey, 'false');
        return this.http.delete<void>(this.sessionUrl);
    }
}
