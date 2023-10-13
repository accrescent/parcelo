// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { Draft, DraftStatus } from '../draft';

@Injectable({
    providedIn: 'root'
})
export class DraftService {
    private readonly draftsUrl = 'api/v1/drafts';

    constructor(private http: HttpClient) {}

    getApproved(): Observable<Draft[]> {
        return this.http.get<Draft[]>(this.draftsUrl)
            .pipe(map(drafts => drafts.filter(draft => draft.status === DraftStatus.Approved)));
    }
}
