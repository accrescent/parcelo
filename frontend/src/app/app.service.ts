import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { App } from './app';

@Injectable({
    providedIn: 'root'
})
export class AppService {
    private readonly appsUrl = 'api/apps';

    constructor(private http: HttpClient) {}

    getApps(): Observable<App[]> {
        return this.http.get<App[]>(this.appsUrl);
    }
}
