import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly sessionUrl = 'api/session';

    constructor(private http: HttpClient) {}

    isLoggedIn(): boolean {
        return localStorage.getItem("loggedIn") == 'true';
    }

    logIn(): void {
        localStorage.setItem("loggedIn", 'true');
    }

    logOut(): Observable<void> {
        localStorage.setItem("loggedIn", 'false');
        return this.http.delete<void>(this.sessionUrl);
    }
}
