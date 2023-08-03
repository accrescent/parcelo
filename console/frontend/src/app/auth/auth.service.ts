import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';

import { Observable, of, map, tap, catchError } from 'rxjs';

import { Permissions, AuthError } from './auth';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly callbackUrl = 'auth/github/callback';
    private readonly sessionUrl = 'api/v1/session';
    private readonly authStorageKey = 'auth';

    constructor(private http: HttpClient) {}

    permissions(): Permissions | null {
        const json = localStorage.getItem(this.authStorageKey);
        if (json !== null) {
            return JSON.parse(json);
        }
        return null;
    }

    logIn(code: string, state: string): Observable<Permissions | AuthError> {
        const header = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');
        const params = new HttpParams().append('code', code).append('state', state);
        return this.http.post<Permissions>(this.callbackUrl, params, { observe: 'response', headers: header })
            .pipe(
                map(res => res.body!!),
                catchError(err => {
                    switch (err.status) {
                    case 401:
                        if (err.error != null && err.error.error_code == 41) {
                            // Backend confirmed that the user wasn't whitelisted.
                            return of(AuthError.NOT_WHITELISTED);
                        } else {
                            // Probably ktor rejecting a stale OAuth token.
                            return of(AuthError.BAD_REQUEST);
                        }
                    case 403:
                        // Some other route failure (CSRF mitigation, malformed account data, etc.) that
                        // should be treated as an invalid login
                        return of(AuthError.BAD_REQUEST);
                    default:
                        return of(AuthError.UNKNOWN_ERROR);
                    }
                }),
                tap(res => {
                    if ((res as Permissions).reviewer) {
                        localStorage.setItem(this.authStorageKey, JSON.stringify(res));
                    }
                })
            );
    }

    logOut(): Observable<void> {
        localStorage.removeItem(this.authStorageKey);
        return this.http.delete<void>(this.sessionUrl);
    }
}
