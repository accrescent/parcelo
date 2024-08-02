// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { RouterLink } from '@angular/router';

import { App } from '../app';
import { AppCardComponent } from '../app-card/app-card.component';
import { AppService } from '../app.service';
import { Draft, DraftStatus } from '../draft';
import { DraftCardComponent } from '../draft-card/draft-card.component';
import {
    DraftDeletionDialogComponent,
} from '../draft-deletion-dialog/draft-deletion-dialog.component';
import { DraftService } from '../draft.service';

@Component({
    selector: 'app-apps-screen',
    standalone: true,
    imports: [
        AppCardComponent,
        DraftCardComponent,
        MatCardModule,
        MatDialogModule,
        MatDividerModule,
        RouterLink,
    ],
    templateUrl: './apps-screen.component.html',
    styleUrl: './apps-screen.component.scss',
})
export class AppsScreenComponent implements OnInit {
    apps: App[] = [];
    drafts: Draft[] = [];

    constructor(
        private appService: AppService,
        private draftService: DraftService,
        private dialog: MatDialog,
    ) {}

    ngOnInit(): void {
        this.appService.getApps().subscribe(apps => this.apps = apps);
        this.draftService.getDrafts().subscribe(drafts => this.drafts = drafts);
    }

    deleteDraft(id: string): void {
        const draft = this.drafts.find(d => d.id === id);

        this.dialog
            .open(DraftDeletionDialogComponent, { data: draft })
            .afterClosed()
            .subscribe(confirmed => {
                if (confirmed) {
                    this.draftService.deleteDraft(id).subscribe(() => {
                        // Remove from the UI
                        const i = this.drafts.findIndex(d => d.id === id);
                        if (i > -1) {
                            this.drafts.splice(i, 1);
                        }
                    });
                }
            });

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
