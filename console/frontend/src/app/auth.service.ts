import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly sessionUrl = 'api/session';

    constructor(private http: HttpClient) {}

    logOut(): Observable<void> {
        return this.http.delete<void>(this.sessionUrl);
    }
}
