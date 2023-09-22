// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Update } from './update';

@Injectable({
    providedIn: 'root'
})
export class UpdateService {
    private readonly appsUrl = 'api/v1/apps';
    private readonly updatesUrl = 'api/v1/updates';

    constructor(private http: HttpClient) {}

    createUpdate(appId: string, apkSet: File): Observable<HttpEvent<Update>> {
        const formData = new FormData();
        formData.append('apk_set', apkSet);

        const req = new HttpRequest(
            'POST',
            `${this.appsUrl}/${appId}/updates`,
            formData, { reportProgress: true },
        );

        return this.http.request(req);
    }

    getUpdates(appId: string): Observable<Update[]> {
        return this.http.get<Update[]>(`${this.appsUrl}/${appId}/updates`);
    }

    submitUpdate(id: string): Observable<void> {
        return this.http.patch<void>(`${this.updatesUrl}/${id}`, '');
    }
}
