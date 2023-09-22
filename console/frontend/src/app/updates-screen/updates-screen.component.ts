// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
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
import { UpdateService } from '../update.service';
import {
    UpdateSubmissionDialogComponent
} from '../update-submission-dialog/update-submission-dialog.component';

@Component({
    selector: 'app-updates-screen',
    standalone: true,
    imports: [
        MatDialogModule,
        MatDividerModule,
        MatProgressBarModule,
        NewUpdateEditorComponent,
        NgFor,
        NgIf,
        UpdateCardComponent,
    ],
    templateUrl: './updates-screen.component.html',
    styleUrls: ['./updates-screen.component.scss'],
})
export class UpdatesScreenComponent implements OnInit {
    app?: App;
    updates: Update[] = [];
    uploadProgress = 0;

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
                                this.updateService.submitUpdate(update.id).subscribe(submittedUpdate => {
                                    // Mark as submitted in the UI
                                    const uiUpdate = this.updates.find(u => {
                                        return u.id === update.id &&
                                            u.status === UpdateStatus.Unsubmitted;
                                    });
                                    if (uiUpdate !== undefined) {
                                        uiUpdate.status = submittedUpdate.status;
                                    }
                                });
                            }
                        });
                }
            });
        }
    }
}
