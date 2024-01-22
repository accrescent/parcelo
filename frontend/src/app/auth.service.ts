// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpStatusCode } from '@angular/common/http';

import { Observable, catchError, map, of, tap, throwError } from 'rxjs';

import { AuthResult } from './auth-result';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly callbackUrl = 'auth/github/callback2';
    private readonly sessionUrl = 'api/v1/session';
    private readonly loggedInStorageKey = 'loggedIn';
    private readonly reviewerStorageKey = 'reviewer';
    private readonly publisherStorageKey = 'publisher';

    constructor(private http: HttpClient) {}

    get loggedIn(): boolean {
        return localStorage.getItem(this.loggedInStorageKey) === 'true';
    }

    get reviewer(): boolean {
        return localStorage.getItem(this.reviewerStorageKey) === 'true';
    }

    get publisher(): boolean {
        return localStorage.getItem(this.publisherStorageKey) === 'true';
    }

    logIn(code: string, state: string): Observable<boolean> {
        const params = new HttpParams().append('code', code).append('state', state);
        return this.http.get<AuthResult>(this.callbackUrl, { observe: 'response', params })
            .pipe(
                tap(res => {
                    const body = res.body!;

                    localStorage.setItem(this.reviewerStorageKey, body.reviewer.toString());
                    localStorage.setItem(this.publisherStorageKey, body.publisher.toString());
                }),
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
        return this.http.delete<void>(this.sessionUrl).pipe(
            tap(() => {
                localStorage.setItem(this.loggedInStorageKey, 'false');
                localStorage.removeItem(this.reviewerStorageKey);
            }),
        );
    }
}
