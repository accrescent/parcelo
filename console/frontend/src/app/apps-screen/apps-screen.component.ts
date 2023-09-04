// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { RouterLink } from '@angular/router';

import { App } from '../app/app';
import { AppCardComponent } from '../app-card/app-card.component';
import { AppService } from '../app/app.service';
import { Draft, DraftStatus } from '../draft';
import { DraftCardComponent } from '../draft-card/draft-card.component';
import { DraftService } from '../draft.service';

@Component({
    selector: 'app-apps-screen',
    standalone: true,
    imports: [
        AppCardComponent,
        DraftCardComponent,
        MatCardModule,
        MatDividerModule,
        NgFor,
        NgIf,
        RouterLink,
    ],
    templateUrl: './apps-screen.component.html',
    styleUrls: ['./apps-screen.component.scss'],
})
export class AppsScreenComponent implements OnInit {
    apps: App[] = [];
    drafts: Draft[] = [];

    constructor(private appService: AppService, private draftService: DraftService) {}

    ngOnInit(): void {
        this.appService.getApps().subscribe(apps => this.apps = apps);
        this.draftService.getDrafts().subscribe(drafts => this.drafts = drafts);
    }

    submitDraft(id: string): void {
        this.draftService.submitDraft(id).subscribe(() => {
            // Mark as submitted in the UI
            const draft = this
                .drafts
                .find(draft => draft.id === id && draft.status === DraftStatus.Unsubmitted);
            if (draft !== undefined) {
                draft.status = DraftStatus.Submitted;
            }
        });
    }
}
