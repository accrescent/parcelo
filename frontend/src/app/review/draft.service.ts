// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Draft } from '../draft';
import { Review } from './review';

@Injectable({
    providedIn: 'root'
})
export class DraftService {
    private readonly draftsUrl = 'api/v1/drafts';
    private readonly assignedDraftsUrl = `${this.draftsUrl}/assigned`;

    constructor(private http: HttpClient) {}

    getAssigned(): Observable<Draft[]> {
        return this.http.get<Draft[]>(this.assignedDraftsUrl);
    }

    createReviewForDraft(draftId: string, review: Review): Observable<Review> {
        return this.http.post<Review>(`${this.draftsUrl}/${draftId}/review`, review);
    }
}
