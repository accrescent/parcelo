import { Injectable } from '@angular/core';
import { HttpClient, HttpEventType, HttpResponse } from '@angular/common/http';

import { Observable, map, catchError, filter } from 'rxjs';

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

    upload(label: string, app: File, icon: File): Observable<Draft | number> {
        const formData = new FormData();
        formData.append("apk_set", app);
        formData.append("icon", icon);
        formData.append("label", label);
        return this.http.post<Draft>(this.draftsUrl, formData, { observe: 'events', reportProgress: true }).pipe(
            filter(event => event.type === HttpEventType.UploadProgress || event instanceof HttpResponse),
            map(event => {
                if (event.type === HttpEventType.UploadProgress) {
                    return Math.round(100 * event.loaded / event.total!!);
                } else if (event instanceof HttpResponse) {
                    return event.body!!;
                } else {
                    throw new Error("unreachable");
                }
            }),
            catchError(err => { 
                if (!err.error) {
                    // Unexpected non-ApiError, make up one 
                    // TODO: Move this to interceptor
                    err.error = {
                        errorCode: -1,
                        title: "Internal server error",
                        message: "Please report the issue"
                    };
                }
                throw err.error; 
            })
        );
    }

    confirm(draft: Draft): Observable<void> {
        return this.http.patch<void>(this.draftsUrl + "/" + draft.id, { observe: 'events'}).pipe(
            catchError(err => { 
                if (!err.error) {
                    // Unexpected non-ApiError, make up one 
                    err.error = {
                        errorCode: -1,
                        title: "Internal server error",
                        message: "Please report the issue"
                    };
                }
                throw err.error; 
            })
        );
    }
}
