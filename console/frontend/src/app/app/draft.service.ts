// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse, HttpEventType } from '@angular/common/http';
import { Observable, map, filter, catchError } from 'rxjs';

import { Draft } from './draft';

@Injectable({
    providedIn: 'root'
})
export class DraftService {
    private readonly draftsUrl = 'api/v1/drafts';

    constructor(private http: HttpClient) {}

    getDrafts(): Observable<Draft[]> {
        return this.http.get<Draft[]>(this.draftsUrl);
    }

    createDraft(apkSet: File, icon: File, label: string): Observable<Draft | number> {
        const formData = new FormData();
        formData.append("apk_set", apkSet);
        formData.append("icon", icon);
        formData.append("label", label);
        return this.http.post<Draft>(this.draftsUrl, formData, { observe: 'events', reportProgress: true }).pipe(
            filter(event => event.type === HttpEventType.UploadProgress || event instanceof HttpResponse),
            map(event => {
                if (event.type === HttpEventType.UploadProgress) {
                    return Math.round(100 * event.loaded / event.total!);
                } else if (event instanceof HttpResponse) {
                    return event.body!;
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
                throw err; 
            })
        );
    }


    submitDraft(id: string): Observable<void> {
        return this.http.patch<void>(`${this.draftsUrl}/${id}`, '').pipe(
            catchError(err => { 
                if (!err.error) {
                    // Unexpected non-ApiError, make up one 
                    err.error = {
                        errorCode: -1,
                        title: "Internal server error",
                        message: "Please report the issue"
                    };
                }
                throw err; 
            })
        );
    }
}
