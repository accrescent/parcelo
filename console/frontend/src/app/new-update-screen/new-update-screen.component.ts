// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';

import { App } from '../app';
import { AppService } from '../app.service';
import { NewUpdateEditorComponent } from '../new-update-editor/new-update-editor.component';
import { NewUpdateForm } from '../new-update-form';
import { UpdateService } from '../update.service';
import {
    UpdateSubmissionDialogComponent
} from '../update-submission-dialog/update-submission-dialog.component';

@Component({
    selector: 'app-new-update-screen',
    standalone: true,
    imports: [MatDialogModule, MatProgressBarModule, NewUpdateEditorComponent],
    templateUrl: './new-update-screen.component.html',
})
export class NewUpdateScreenComponent implements OnInit {
    app?: App;
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
                    this.dialog
                        .open(UpdateSubmissionDialogComponent, {
                            data: { app: this.app, update: update },
                        })
                        .afterClosed()
                        .subscribe(confirmed => {
                            if (confirmed) {
                                this.updateService.submitUpdate(update.id).subscribe(() => {
                                    this.router.navigate(['apps']);
                                });
                            } else {
                                this.router.navigate(['apps']);
                            }
                        });
                }
            });
        }
    }
}
