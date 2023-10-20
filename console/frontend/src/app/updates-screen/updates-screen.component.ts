// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';

import { App } from '../app';
import { AppService } from '../app.service';
import { NewUpdateEditorComponent } from '../new-update-editor/new-update-editor.component';
import { NewUpdateForm } from '../new-update-form';
import { Update, UpdateStatus } from '../update';
import { UpdateCardComponent } from '../update-card/update-card.component';
import { UpdateFilterPipe } from '../update-filter.pipe';
import { UpdateService } from '../update.service';
import {
    UpdateDeletionDialogComponent,
} from '../update-deletion-dialog/update-deletion-dialog.component';
import {
    UpdateSubmissionDialogComponent
} from '../update-submission-dialog/update-submission-dialog.component';

@Component({
    selector: 'app-updates-screen',
    standalone: true,
    imports: [
        MatChipsModule,
        MatDialogModule,
        MatDividerModule,
        MatProgressBarModule,
        NewUpdateEditorComponent,
        NgFor,
        NgIf,
        UpdateCardComponent,
        UpdateFilterPipe,
    ],
    templateUrl: './updates-screen.component.html',
    styleUrls: ['./updates-screen.component.scss'],
})
export class UpdatesScreenComponent implements OnInit {
    app?: App;
    updates: Update[] = [];
    uploadProgress = 0;

    showRejected = false;
    showPublished = false;

    constructor(
        private activatedRoute: ActivatedRoute,
        private appService: AppService,
        private dialog: MatDialog,
        private router: Router,
        private updateService: UpdateService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap.subscribe(params => {
            // TODO: Handle error case
            const appId = params.get('id');
            if (appId !== null) {
                this.appService.getApp(appId).subscribe(app => this.app = app);
                this.updateService.getUpdates(appId).subscribe(updates => this.updates = updates);
            }
        });
    }

    submitNewUpdate(form: NewUpdateForm): void {
        if (this.app !== undefined) {
            this.updateService.createUpdate(this.app.id, form.apkSet).subscribe(event => {
                if (event.type === HttpEventType.UploadProgress) {
                    this.uploadProgress = 100 * event.loaded / event.total!;

                    // Clear the progress bar once the upload is complete
                    if (event.loaded === event.total!) {
                        this.uploadProgress = 0;
                    }
                } else if (event instanceof HttpResponse) {
                    const update = event.body!;

                    this.updates.push(update);
                    this.dialog
                        .open(UpdateSubmissionDialogComponent, {
                            data: { app: this.app, update: update },
                        })
                        .afterClosed()
                        .subscribe(confirmed => {
                            if (confirmed) {
                                this.submitUpdate(update.id);
                            }
                        });
                }
            });
        }
    }

    submitUpdate(id: string): void {
        this.updateService.submitUpdate(id).subscribe(submittedUpdate => {
            // Mark as submitted in the UI
            const update = this
                .updates
                .find(update => update.id === id && update.status === UpdateStatus.Unsubmitted);
            if (update !== undefined) {
                update.status = submittedUpdate.status;
            }
        });
    }

    deleteUpdate(id: string): void {
        const update = this.updates.find(update => update.id === id);

        this.dialog
            .open(UpdateDeletionDialogComponent, { data: update })
            .afterClosed()
            .subscribe(confirmed => {
                if (confirmed) {
                    this.updateService.deleteUpdate(id).subscribe(() => {
                        // Remove update from the UI
                        const i = this.updates.findIndex(update => update.id === id);
                        if (i > -1) {
                            this.updates.splice(i, 1);
                        }
                    });
                }
            });
    }
}
