// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Edit } from './edit';
import { NewEditForm } from './new-edit-form';
import { environment } from '../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class EditService {
    private readonly appsUrl = `${environment.developerApiUrl}/api/v1/apps`;
    private readonly editsUrl = `${environment.developerApiUrl}/api/v1/edits`;

    constructor(private http: HttpClient) {}

    createEdit(appId: string, edit: NewEditForm): Observable<HttpEvent<Edit>> {
        const formData = new FormData();
        if (edit.shortDescription !== undefined) {
            formData.append('short_description', edit.shortDescription);
        }

        const req = new HttpRequest('POST', `${this.appsUrl}/${appId}/edits`, formData);

        return this.http.request(req);
    }

    getEdits(appId: string): Observable<Edit[]> {
        return this.http.get<Edit[]>(`${this.appsUrl}/${appId}/edits`);
    }

    submitEdit(editId: string): Observable<void> {
        return this.http.patch<void>(`${this.editsUrl}/${editId}`, '');
    }

    deleteEdit(id: string): Observable<void> {
        return this.http.delete<void>(`${this.editsUrl}/${id}`);
    }
}
