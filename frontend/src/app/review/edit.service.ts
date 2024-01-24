// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Edit } from '../edit';
import { Review } from './review';

@Injectable({
    providedIn: 'root'
})
export class EditService {
    private readonly editsUrl = 'api/v1/edits';
    private readonly assignedEditsUrl = `${this.editsUrl}/assigned`;

    constructor(private http: HttpClient) {}

    getAssigned(): Observable<Edit[]> {
        return this.http.get<Edit[]>(this.assignedEditsUrl);
    }

    createReviewForEdit(editId: string, review: Review): Observable<Review> {
        return this.http.post<Review>(`${this.editsUrl}/${editId}/review`, review);
    }
}
