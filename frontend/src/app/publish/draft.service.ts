// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Draft } from '../draft';

@Injectable({
    providedIn: 'root'
})
export class DraftService {
    private readonly approvedDraftsUrl = 'api/v1/drafts/approved';

    constructor(private http: HttpClient) {}

    getApproved(): Observable<Draft[]> {
        return this.http.get<Draft[]>(this.approvedDraftsUrl);
    }
}
