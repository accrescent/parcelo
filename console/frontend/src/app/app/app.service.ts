import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest, HttpEventType, HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable, map, catchError, of } from 'rxjs';

import { App, Draft, DraftError } from './app';

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

    upload(label: string, app: File, icon: File): Observable<Draft | DraftError | number | undefined> {
        const formData = new FormData();
        formData.append("apk_set", app);
        formData.append("icon", icon);
        formData.append("label", label);
        return this.http.post<Draft>(this.draftsUrl, formData, { observe: 'events', reportProgress: true }).pipe(
            catchError(err => {
                return of(err.error);
            }),
            map(event => {
                if ((event as DraftError).title !== undefined) {
                    return event;
                }

                if (event.type === HttpEventType.UploadProgress) {
                    return Math.round(100 * event.loaded / event.total!!);
                } else if (event instanceof HttpResponse) {
                    return event.body!!;
                } else {
                    return undefined;
                }
            })
        );
    }
}
