import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';

import { Observable } from 'rxjs';

import { App, Draft } from './app';

@Injectable({
    providedIn: 'root'
})
export class AppService {
    private readonly appsUrl = 'api/v1/apps';
    private readonly draftsUrl = 'api/v1/drafts';

    constructor(private http: HttpClient) {}

    getApps(): Observable<App[]> {
        return this.http.get<App[]>(this.appsUrl);
    }

    upload(label: string, app: File, icon: File): Observable<HttpEvent<Draft>> {
        const formData = new FormData();
        formData.append("apk_set", app);
        formData.append("icon", icon);
        formData.append("label", label);
        return this.http.post<Draft>(this.draftsUrl, formData, { observe: 'events' });
    }
}
