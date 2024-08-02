// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Draft } from './draft';
import { NewDraftForm } from './new-draft-form';

@Injectable({
    providedIn: 'root'
})
export class DraftService {
    private readonly draftsUrl = 'api/v1/drafts';

    constructor(private http: HttpClient) {}

    createDraft(form: NewDraftForm): Observable<HttpEvent<Draft>> {
        const formData = new FormData();
        formData.append('icon', form.icon);
        formData.append('apk_set', form.apkSet);
        formData.append('label', form.label);
        formData.append('short_description', form.shortDescription);

        const req = new HttpRequest('POST', this.draftsUrl, formData, { reportProgress: true });

        return this.http.request(req);
    }

    getDrafts(): Observable<Draft[]> {
        return this.http.get<Draft[]>(this.draftsUrl);
    }

    submitDraft(id: string): Observable<void> {
        return this.http.patch<void>(`${this.draftsUrl}/${id}`, '');
    }

    deleteDraft(id: string): Observable<void> {
        return this.http.delete<void>(`${this.draftsUrl}/${id}`);
    }
}
