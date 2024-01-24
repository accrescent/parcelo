// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Review } from './review';
import { Update } from '../update';

@Injectable({
    providedIn: 'root'
})
export class UpdateService {
    private readonly updatesUrl = 'api/v1/updates';
    private readonly assignedUpdatesUrl = `${this.updatesUrl}/assigned`;

    constructor(private http: HttpClient) {}

    getAssigned(): Observable<Update[]> {
        return this.http.get<Update[]>(this.assignedUpdatesUrl);
    }

    createReviewForUpdate(updateId: string, review: Review): Observable<Review> {
        return this.http.post<Review>(`${this.updatesUrl}/${updateId}/review`, review);
    }
}
