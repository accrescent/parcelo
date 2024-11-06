// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Update } from '../../update';
import { environment } from '../../../environments/environment';

@Component({
    selector: 'app-reviewer-update-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './reviewer-update-card.component.html',
})
export class ReviewerUpdateCardComponent implements OnInit {
    @Input({ required: true }) update!: Update;
    @Output() postReview = new EventEmitter<string>();
    apkSetLink?: string;

    ngOnInit(): void {
        this.apkSetLink = `${environment.developerApiUrl}/api/v1/updates/${this.update.id}/apkset`;
    }

    onPostReview(): void {
        this.postReview.emit(this.update.id);
    }
}
