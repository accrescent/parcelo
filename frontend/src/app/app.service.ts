// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { App } from './app';

@Injectable({
    providedIn: 'root'
})
export class AppService {
    private readonly appsUrl = 'api/v1/apps';

    constructor(private http: HttpClient) {}

    getApp(id: string): Observable<App> {
        return this.http.get<App>(`${this.appsUrl}/${id}`);
    }

    getApps(): Observable<App[]> {
        return this.http.get<App[]>(this.appsUrl);
    }
}
