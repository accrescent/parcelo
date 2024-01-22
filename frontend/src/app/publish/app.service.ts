// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AppService {
    private readonly appsUrl = 'api/v1/apps';

    constructor(private http: HttpClient) {}

    publishDraft(draftId: string): Observable<void> {
        return this.http.post<void>(this.appsUrl, { draft_id: draftId });
    }
}
