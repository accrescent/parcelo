import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse, HttpHeaders } from '@angular/common/http';

import { Observable, map } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly callbackUrl = 'auth/github/callback';
    private readonly sessionUrl = 'api/v1/session';

    constructor(private http: HttpClient) {}

    isLoggedIn(): boolean {
        return localStorage.getItem("loggedIn") == 'true';
    }

    logIn(code: string, state: string): Observable<boolean> {
        const header = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');
        const params = new HttpParams().append('code', code).append('state', state);
        return this.http.post<HttpResponse<string>>(this.callbackUrl, params, { observe: 'response', headers: header })
            .pipe(
                map(res => {
                    // TODO: There has to be another way to do this
                    const result = res && res.status != 401;
                    localStorage.setItem("loggedIn", result.toString());
                    return result;
                })
            );
    }

    logOut(): Observable<void> {
        localStorage.setItem("loggedIn", 'false');
        return this.http.delete<void>(this.sessionUrl);
    }
}
