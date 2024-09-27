// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { NewUpdateForm } from './new-update-form';
import { Update } from './update';
import { environment } from '../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class UpdateService {
    private readonly appsUrl = `${environment.developerApiUrl}/api/v1/apps`;
    private readonly updatesUrl = `${environment.developerApiUrl}/api/v1/updates`;

    constructor(private http: HttpClient) {}

    createUpdate(appId: string, form: NewUpdateForm): Observable<HttpEvent<Update>> {
        const formData = new FormData();
        formData.append('apk_set', form.apkSet);

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

    submitUpdate(id: string): Observable<Update> {
        return this.http.patch<Update>(`${this.updatesUrl}/${id}`, '');
    }

    deleteUpdate(id: string): Observable<void> {
        return this.http.delete<void>(`${this.updatesUrl}/${id}`);
    }
}
