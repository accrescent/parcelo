import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse, HttpHeaders } from '@angular/common/http';

import { Observable, tap, map } from 'rxjs';

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

    logIn(code: string, state: string): Observable<boolean> {
        const header = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');
        const params = new HttpParams().append('code', code).append('state', state);
        return this.http.post<HttpResponse<string>>(this.callbackUrl, params, { observe: 'response', headers: header })
            .pipe(
                map(res => res && res.status != 401),
                tap(res => localStorage.setItem(this.loggedInStorageKey, res.toString()))
            );
    }

    logOut(): Observable<void> {
        localStorage.setItem(this.loggedInStorageKey, 'false');
        return this.http.delete<void>(this.sessionUrl);
    }
}
