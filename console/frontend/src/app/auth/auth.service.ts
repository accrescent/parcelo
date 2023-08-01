import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';

import { Observable, of, tap, map, catchError } from 'rxjs';

import { AuthResult } from './auth.result';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly callbackUrl = 'auth/github/callback';
    private readonly sessionUrl = 'api/v1/session';
    private readonly loggedInStorageKey = 'loggedIn';

    constructor(private http: HttpClient) {}

    isLoggedIn(): boolean {
        return localStorage.getItem("loggedIn") == 'true';
    }

    logIn(code: string, state: string): Observable<AuthResult> {
        const header = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');
        const params = new HttpParams().append('code', code).append('state', state);
        return this.http.post<AuthResult>(this.callbackUrl, params, { observe: 'response', headers: header })
            .pipe(
                map(() => AuthResult.OK),
                catchError(err => {
                    switch (err.status) {
                    case 401:
                        if (err.error != null && err.error.error_code == 41) {
                            // Backend confirmed that the user wasn't whitelisted.
                            return of(AuthResult.NOT_WHITELISTED);
                        } else {
                            // Probably ktor rejecting a stale OAuth token.
                            return of(AuthResult.BAD_REQUEST);
                        }
                    case 403:
                        // Some other route failure (CSRF mitigation, malformed account data, etc.) that
                        // should be treated as an invalid login
                        return of(AuthResult.BAD_REQUEST);
                    default:
                        return of(AuthResult.UNKNOWN_ERROR);
                    }
                }),
                tap(res => localStorage.setItem(this.loggedInStorageKey, (res === AuthResult.OK).toString()))
            );
    }

    logOut(): Observable<void> {
        localStorage.setItem(this.loggedInStorageKey, 'false');
        return this.http.delete<void>(this.sessionUrl);
    }
}
