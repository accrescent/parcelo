// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { App } from '../app';
import { AppService } from '../app.service';
import { Edit, EditStatus } from '../edit';
import { EditCardComponent } from '../edit-card/edit-card.component';
import { EditDeletionDialogComponent } from '../edit-deletion-dialog/edit-deletion-dialog.component';
import { EditFilterPipe } from '../edit-filter.pipe';
import { EditService } from '../edit.service';
import {
    EditSubmissionDialogComponent,
} from '../edit-submission-dialog/edit-submission-dialog.component';
import { NewEditEditorComponent } from '../new-edit-editor/new-edit-editor.component';
import { NewEditForm } from '../new-edit-form';
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
    selector: 'app-app-details-screen',
    standalone: true,
    imports: [
        EditCardComponent,
        EditFilterPipe,
        MatChipsModule,
        MatDialogModule,
        MatDividerModule,
        MatFormFieldModule,
        MatInputModule,
        MatProgressBarModule,
        NewEditEditorComponent,
        NewUpdateEditorComponent,
        UpdateCardComponent,
        UpdateFilterPipe,
    ],
    templateUrl: './app-details-screen.component.html',
    styleUrl: './app-details-screen.component.scss',
})
export class AppDetailsScreenComponent implements OnInit {
    app?: App;
    updates: Update[] = [];
    edits: Edit[] = [];
    uploadProgress?: number = undefined;

    showRejectedEdits = false;
    showPublishedEdits = false;
    showRejectedUpdates = false;
    showPublishedUpdates = false;
    submitDisabled = false;

    constructor(
        private activatedRoute: ActivatedRoute,
        private appService: AppService,
        private dialog: MatDialog,
        private editService: EditService,
        private router: Router,
        private updateService: UpdateService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap.subscribe(params => {
            // TODO: Handle error case
            const appId = params.get('id');
            if (appId !== null) {
                this.appService.getApp(appId).subscribe(app => this.app = app);
                this.editService.getEdits(appId).subscribe(edits => this.edits = edits);
                this.updateService.getUpdates(appId).subscribe(updates => this.updates = updates);
            }
        });
    }

    createUpdate(form: NewUpdateForm): void {
        if (this.app !== undefined) {
            this.submitDisabled = true;
            this.updateService
                .createUpdate(this.app.id, form)
                .pipe(finalize(() => this.submitDisabled = false))
                .subscribe(event => {
                    if (event.type === HttpEventType.UploadProgress) {
                        this.uploadProgress = 100 * event.loaded / event.total!;

                        // Clear the progress bar once the upload is complete
                        if (event.loaded === event.total!) {
                            this.uploadProgress = undefined;
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

    createEdit(form: NewEditForm): void {
        if (this.app !== undefined) {
            this.editService.createEdit(this.app.id, form).subscribe(event => {
                if (event instanceof HttpResponse) {
                    const edit = event.body!;

                    this.edits.push(edit);
                    this.dialog
                        .open(EditSubmissionDialogComponent, {
                            data: { app: this.app, edit: edit },
                        })
                        .afterClosed()
                        .subscribe(confirmed => {
                            if (confirmed) {
                                this.submitEdit(edit.id);
                            }
                        });
                }
            });
        }
    }

    submitEdit(id: string): void {
        this.editService.submitEdit(id).subscribe(() => {
            // Mark as submitted in the UI
            const edit = this
                .edits
                .find(edit => edit.id === id && edit.status === EditStatus.Unsubmitted);
            if (edit !== undefined) {
                edit.status = EditStatus.Submitted;
            }
        });
    }

    deleteEdit(id: string): void {
        const edit = this.edits.find(edit => edit.id === id);

        this.dialog
            .open(EditDeletionDialogComponent, { data: edit })
            .afterClosed()
            .subscribe(confirmed => {
                if (confirmed) {
                    this.editService.deleteEdit(id).subscribe(() => {
                        // Remove update from the UI
                        const i = this.edits.findIndex(edit => edit.id === id);
                        if (i > -1) {
                            this.edits.splice(i, 1);
                        }
                    });
                }
            });
    }
}
